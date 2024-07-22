package io.gitlab.wylieyyyy.piptube

import javafx.beans.value.ChangeListener
import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.input.MouseButton
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
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Stack
import javax.swing.JFrame

class FXMLController(private val frame: JFrame) {
    companion object {
        public const val BASE_WIDTH = 640

        public const val BASE_HEIGHT = 360
    }

    public val parent: Parent

    @FXML private lateinit var searchField: TextField

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
    private val windowBoundsHandler = WindowBoundsHandler(frame, BASE_HEIGHT)

    init {
        val loader = FXMLLoader(this::class.java.getResource("scene.fxml"))
        loader.setController(this)
        parent = loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        searchField.focusedProperty().addListener(
            ChangeListener { _, _, newValue ->
                frame.focusableWindowState = newValue

                if (newValue) {
                    frame.toFront()
                    searchField.requestFocus()
                } else {
                    frame.setVisible(false)
                    frame.setVisible(true)
                }
            },
        )
        searchField.onAction = handler { _ -> onSearchFieldActioned() }
        videoView.onMouseClicked =
            handler { event ->
                if (event.button == MouseButton.SECONDARY) onBack()
            }
        videoArea.onScroll = handler(windowBoundsHandler::handleScroll)
        playButton.onAction = handler { _ -> onPlayButtonActioned() }
        windowBoundsHandler.moveToBottomRight()
    }

    private suspend fun onSearchFieldActioned() {
        videoList.children.clear()
        videoArea.requestFocus()
        // TODO: ParsingException
        val searchQueryHandler =
            youtubeService.searchQHFactory.fromQuery(
                searchField.text,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                null,
            )
        scope.launch(Dispatchers.IO) {
            val extractor = youtubeService.getSearchExtractor(searchQueryHandler)
            // TODO: IOException, ExtractionException
            extractor.fetchPage()

            withContext(Dispatchers.Main) {
                videoList.children.addAll(
                    // TODO: IOException, ExtractionException
                    extractor.getInitialPage().items.filterIsInstance<StreamInfoItem>().map {
                        VideoListEntryControl(it) {
                            scope.launch {
                                windowBoundsHandler.resizeToBase()
                                videoStack.push(updateVideo(it.url))
                            }
                        }
                    },
                )
            }
        }
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
                        scope.launch {
                            windowBoundsHandler.resizeToBase()
                            videoStack.push(updateVideo(it.url))
                        }
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
