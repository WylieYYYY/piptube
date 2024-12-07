package io.gitlab.wylieyyyy.piptube

import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.HorizontalDirection
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.StackPane
import javafx.scene.shape.Rectangle
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamExtractor
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.awt.Point
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
    }

    @FXML private lateinit var videoView: ImageView

    @FXML private lateinit var progressBackgroundRectangle: Rectangle

    @FXML private lateinit var progressRectangle: Rectangle

    @FXML private lateinit var titleLabel: Label

    @FXML private lateinit var progressLabel: Label

    @FXML private lateinit var progress: ProgressIndicator

    private var isMouseEventDrag = false
    private val videoViewCoroutineScope = MainScope()
    private val mediaPlayerFactory = MediaPlayerFactory()
    private val embeddedMediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer()

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

        embeddedMediaPlayer.videoSurface().set(ImageViewVideoSurface(videoView))

        embeddedMediaPlayer.events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun buffering(
                    mediaPlayer: MediaPlayer,
                    newCache: Float,
                ) {
                    progress.setVisible(newCache != 100.toFloat())
                }

                override fun timeChanged(
                    mediaPlayer: MediaPlayer,
                    newTime: Long,
                ) {
                    scope.launch { updateVideoProgress() }
                }
            },
        )

        videoView.onMouseClicked =
            handler {
                if (isMouseEventDrag) return@handler

                if (it.button == MouseButton.PRIMARY) {
                    embeddedMediaPlayer.controls().pause()
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
                progressLabel.setVisible(true)
                progressBackgroundRectangle.height = SEEKBAR_EXPANDED_HEIGHT.toDouble()
                progressRectangle.height = SEEKBAR_EXPANDED_HEIGHT.toDouble()
            }
        onMouseExited =
            handler {
                titleLabel.setVisible(false)
                progressLabel.setVisible(false)
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
        progressLabel.managedProperty().bind(progressLabel.visibleProperty())
    }

    public suspend fun updateVideo(url: String): StreamExtractor {
        embeddedMediaPlayer.controls().stop()
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
        embeddedMediaPlayer.controls().stop()
        progress.setVisible(true)
        videoViewCoroutineScope.coroutineContext.cancelChildren()

        // TODO: ParsingException
        titleLabel.text = extractor.name

        scope.launch {
            // TODO: ExtractionException, no video stream
            val stream = extractor.videoStreams?.firstOrNull()!!
            // TODO: fail play
            embeddedMediaPlayer.media().play(stream.content)
        }
    }

    private fun updateVideoProgress() {
        fun Long.toDurationString(): String {
            return toDuration(DurationUnit.MILLISECONDS).toComponents { hours, minutes, seconds, _ ->
                val minuteSecondPart =
                    "${minutes.toString().padStart(2, '0')}:" +
                        "${seconds.toString().padStart(2, '0')}"
                if (hours != 0L) {
                    "${hours.toString().padStart(2, '0')}:$minuteSecondPart"
                } else {
                    minuteSecondPart
                }
            }
        }

        progressRectangle.width = embeddedMediaPlayer.status().time().toDouble() /
            embeddedMediaPlayer.status().length() * SEEKBAR_WIDTH
        progressLabel.text =
            embeddedMediaPlayer.status().run {
                time().toDurationString() + '/' + length().toDurationString()
            }
    }

    private suspend fun handleSeekbarClicked(event: MouseEvent) {
        embeddedMediaPlayer.controls().setPosition((event.x / SEEKBAR_WIDTH).toFloat())
        updateVideoProgress()
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
