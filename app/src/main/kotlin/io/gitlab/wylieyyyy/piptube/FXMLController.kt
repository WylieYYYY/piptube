package io.gitlab.wylieyyyy.piptube

import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.input.MouseButton
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Stack
import javax.swing.JFrame

class FXMLController {
    companion object {
        public const val BASE_WIDTH = 640

        public const val BASE_HEIGHT = 360
    }

    @FXML private lateinit var videoList: VBox

    @FXML private lateinit var videoArea: StackPane

    @FXML private lateinit var videoView: MediaView

    @FXML private lateinit var playButton: Button

    private val scope = MainScope()
    private val youtubeService =
        run {
            NewPipe.init(DownloaderImpl)
            NewPipe.getService("YouTube")
        }
    private val videoStack = Stack<StreamExtractor>()

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        videoView.onMouseClicked =
            handler { event ->
                if (event.button == MouseButton.SECONDARY) onBack()
            }
        videoArea.onScroll = handler(::onVideoAreaScrolled)
        playButton.onAction = handler { _ -> onPlayButtonActioned() }
    }

    private suspend fun onBack() {
        val lastVideo = videoStack.pop()
        if (videoStack.empty()) {
            videoStack.push(lastVideo)
        } else {
            updateVideo(videoStack.peek())
        }
    }

    private suspend fun updateVideo(extractor: StreamExtractor) {
        videoView.mediaPlayer?.dispose()
        videoList.children.clear()

        scope.launch {
            // TODO: ExtractionException
            val relatedInfo = extractor.relatedItems
            val relatedStreams = relatedInfo?.items?.filterIsInstance<StreamInfoItem>() ?: listOf()
            videoList.children.addAll(
                relatedStreams.map {
                    VideoListEntryControl(it) {
                        scope.launch { videoStack.push(updateVideo(it.url)) }
                    }
                },
            )
        }
        scope.launch {
            // TODO: ExtractionException, no video stream
            val stream = extractor.videoStreams?.firstOrNull()!!
            val media = Media(stream.content)
            val player = MediaPlayer(media)

            videoView.mediaPlayer = player
            player.play()
        }
    }

    private suspend fun updateVideo(url: String): StreamExtractor {
        videoView.mediaPlayer?.dispose()
        videoList.children.clear()

        val deferredExtractor =
            scope.async(Dispatchers.IO) {
                // TODO: ParsingException
                val streamLinkHandler = youtubeService.streamLHFactory.fromUrl(url)
                val extractor = youtubeService.getStreamExtractor(streamLinkHandler)
                // TODO: ExtractionException, IOException
                extractor.fetchPage()
                extractor
            }

        scope.launch(Dispatchers.IO) {
            val extractor = deferredExtractor.await()
            withContext(Dispatchers.Main) { updateVideo(extractor) }
        }
        return withContext(Dispatchers.IO) { deferredExtractor.await() }
    }

    private suspend fun onVideoAreaScrolled(event: ScrollEvent) {
        val frame = videoView.scene.userData as JFrame

        val oldBounds = frame.bounds
        val verticalInset = frame.insets.top + frame.insets.bottom

        val height =
            (oldBounds.height + event.deltaY).toInt()
                .coerceIn(BASE_HEIGHT + verticalInset, BASE_HEIGHT * 2)
        val deltaHeight = height - oldBounds.height

        frame.setBounds(oldBounds.x, oldBounds.y - deltaHeight, oldBounds.width, height)
    }

    private suspend fun onPlayButtonActioned() {
        playButton.setVisible(false)
        videoStack.push(updateVideo("https://youtube.com/watch?v=EdHGrnuCEo4"))
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}
