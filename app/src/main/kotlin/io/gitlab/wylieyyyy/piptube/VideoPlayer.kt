package io.gitlab.wylieyyyy.piptube

import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.input.MouseButton
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.scene.shape.Rectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamExtractor
import javax.swing.JWindow

class VideoPlayer(
    private val controller: FXMLController,
    private val window: JWindow,
    private val windowBoundsHandler: WindowBoundsHandler,
    private val scope: CoroutineScope,
) : StackPane() {
    companion object {
        public const val SEEKBAR_OFFSET = 20

        public const val SEEKBAR_HEIGHT = 3

        private const val VIDEO_PROGRESS_UPDATE_INTERVAL_MILLISECONDS = 1000L
    }

    @FXML private lateinit var videoView: MediaView

    @FXML private lateinit var progressRectangle: Rectangle

    private var dragOffset = Pair(0, 0)

    init {
        val loader = FXMLLoader(this::class.java.getResource("video_player.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        videoView.onMouseClicked =
            handler { event ->
                if (event.button == MouseButton.SECONDARY) controller.onBack()
            }
        onScroll = handler(windowBoundsHandler::handleScroll)
        onMousePressed =
            handler {
                dragOffset = Pair(window.x - it.screenX.toInt(), window.y - it.screenY.toInt())
            }
        onMouseDragged =
            handler {
                val (xOffset, yOffset) = dragOffset
                window.setLocation(it.screenX.toInt() + xOffset, it.screenY.toInt() + yOffset)
            }
    }

    public fun updateVideo(extractor: StreamExtractor) {
        videoView.mediaPlayer?.dispose()
        scope.coroutineContext.cancelChildren()

        scope.launch {
            // TODO: ExtractionException, no video stream
            val stream = extractor.videoStreams?.firstOrNull()!!
            val media = Media(stream.content)
            val player = MediaPlayer(media)

            scope.launch {
                // TODO: ParsingException
                if (extractor.length > 0) updateVideoProgress(extractor.length)
            }

            videoView.mediaPlayer = player
            player.play()
        }
    }

    public fun disposeMedia() {
        videoView.mediaPlayer?.dispose()
    }

    private tailrec suspend fun updateVideoProgress(length: Long) {
        val time = videoView.mediaPlayer.currentTime.toSeconds()
        progressRectangle.width = time / length * FXMLController.BASE_WIDTH
        delay(VIDEO_PROGRESS_UPDATE_INTERVAL_MILLISECONDS)
        updateVideoProgress(length)
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}