package io.gitlab.wylieyyyy.piptube

import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.HorizontalDirection
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaPlayer.Status.DISPOSED
import javafx.scene.media.MediaPlayer.Status.HALTED
import javafx.scene.media.MediaPlayer.Status.PAUSED
import javafx.scene.media.MediaPlayer.Status.PLAYING
import javafx.scene.media.MediaPlayer.Status.READY
import javafx.scene.media.MediaPlayer.Status.STALLED
import javafx.scene.media.MediaPlayer.Status.STOPPED
import javafx.scene.media.MediaPlayer.Status.UNKNOWN
import javafx.scene.media.MediaView
import javafx.scene.shape.Rectangle
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.awt.Point

class VideoPlayer(
    private val streamingService: StreamingService,
    private val controller: FXMLController,
    private val windowBoundsHandler: WindowBoundsHandler,
    private val scope: CoroutineScope,
) : StackPane() {
    companion object {
        public const val SEEKBAR_OFFSET = 20

        public const val SEEKBAR_WIDTH = FXMLController.BASE_WIDTH

        public const val SEEKBAR_HEIGHT = 3

        public const val SEEKBAR_EXPANDED_HEIGHT = SEEKBAR_HEIGHT * 3

        private const val VIDEO_PROGRESS_UPDATE_INTERVAL_MILLISECONDS = 1000L
    }

    @FXML private lateinit var videoView: MediaView

    @FXML private lateinit var progressBackgroundRectangle: Rectangle

    @FXML private lateinit var progressRectangle: Rectangle

    @FXML private lateinit var titleLabel: Label

    @FXML private lateinit var progress: ProgressIndicator

    private var isMouseEventDrag = false
    private val videoViewCoroutineScope = MainScope()

    init {
        val loader = FXMLLoader(this::class.java.getResource("video_player.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        stylesheets.add(this::class.java.getResource("video_player.css").toString())

        videoView.onMouseClicked =
            handler {
                if (isMouseEventDrag) return@handler

                if (it.button == MouseButton.PRIMARY) {
                    when (videoView.mediaPlayer.status!!) {
                        READY, PLAYING -> videoView.mediaPlayer?.pause()
                        PAUSED, STALLED, STOPPED -> videoView.mediaPlayer?.play()
                        DISPOSED, HALTED, UNKNOWN -> Unit
                    }
                }
                if (it.button == MouseButton.SECONDARY) controller.onBack()
                if (it.button == MouseButton.MIDDLE && !windowBoundsHandler.resizeToBase()) {
                    windowBoundsHandler.moveToBottom(!windowBoundsHandler.horizontalDirection())
                }
            }
        progressBackgroundRectangle.onMouseClicked = handler(::handleSeekbarClicked)
        progressRectangle.onMouseClicked = handler(::handleSeekbarClicked)
        onMouseEntered =
            handler {
                titleLabel.setVisible(true)
                progressBackgroundRectangle.height = SEEKBAR_EXPANDED_HEIGHT.toDouble()
                progressRectangle.height = SEEKBAR_EXPANDED_HEIGHT.toDouble()
            }
        onMouseExited =
            handler {
                titleLabel.setVisible(false)
                progressBackgroundRectangle.height = SEEKBAR_HEIGHT.toDouble()
                progressRectangle.height = SEEKBAR_HEIGHT.toDouble()
            }
        onScroll =
            handler {
                if (it.deltaY < 0 && !controller.scrollControlPane(it)) {
                    windowBoundsHandler.handleScroll(it)
                } else if (it.deltaY > 0 && !windowBoundsHandler.handleScroll(it)) {
                    controller.scrollControlPane(it)
                }
            }
        onMousePressed =
            handler {
                isMouseEventDrag = false
                windowBoundsHandler.prepareMove(Point(it.screenX.toInt(), it.screenY.toInt()))
            }
        onMouseDragged =
            handler {
                isMouseEventDrag = true
                windowBoundsHandler.updateMove(Point(it.screenX.toInt(), it.screenY.toInt()))
            }
        titleLabel.managedProperty().bind(titleLabel.visibleProperty())
    }

    public suspend fun updateVideo(url: String): StreamExtractor {
        videoView.mediaPlayer?.dispose()
        progress.setVisible(true)

        val extractor =
            withContext(Dispatchers.IO) {
                // TODO: ParsingException
                val streamLinkHandler = streamingService.streamLHFactory.fromUrl(url)
                val extractor = streamingService.getStreamExtractor(streamLinkHandler)
                // TODO: ExtractionException, IOException
                extractor.fetchPage()
                extractor
            }

        updateVideo(extractor)
        return extractor
    }

    public fun updateVideo(extractor: StreamExtractor) {
        videoView.mediaPlayer?.dispose()
        progress.setVisible(true)
        videoViewCoroutineScope.coroutineContext.cancelChildren()

        // TODO: ParsingException
        titleLabel.text = extractor.name

        scope.launch {
            // TODO: ExtractionException, no video stream
            val stream = extractor.videoStreams?.firstOrNull()!!
            // TODO: IllegalArgumentException, UnsupportedOperationException, MediaException
            val media = Media(stream.content)
            // TODO: MediaException
            val player = MediaPlayer(media)

            videoViewCoroutineScope.launch {
                // TODO: ParsingException
                if (extractor.length > 0) updateVideoProgress(true)
            }

            player.onStalled =
                object : Runnable {
                    override fun run() = progress.setVisible(true)
                }
            player.onPlaying =
                object : Runnable {
                    override fun run() = progress.setVisible(false)
                }

            videoView.mediaPlayer = player
            player.play()
        }
    }

    private tailrec suspend fun updateVideoProgress(repeat: Boolean) {
        progressRectangle.width =
            videoView.mediaPlayer.run {
                currentTime.toMillis() / totalDuration.toMillis() * SEEKBAR_WIDTH
            }

        if (repeat) {
            delay(VIDEO_PROGRESS_UPDATE_INTERVAL_MILLISECONDS)
            updateVideoProgress(repeat)
        }
    }

    private suspend fun handleSeekbarClicked(event: MouseEvent) {
        videoView.mediaPlayer?.run {
            val duration = totalDuration * event.x / SEEKBAR_WIDTH.toDouble()
            seek(duration)
            updateVideoProgress(false)
        }
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}

operator fun Duration.times(other: Double): Duration = multiply(other)

operator fun Duration.div(other: Double): Duration = divide(other)

operator fun HorizontalDirection.not() =
    if (this == HorizontalDirection.RIGHT) {
        HorizontalDirection.LEFT
    } else {
        HorizontalDirection.RIGHT
    }
