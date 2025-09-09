package io.gitlab.wylieyyyy.piptube

import io.gitlab.wylieyyyy.piptube.player.StatusBar
import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.HorizontalDirection
import javafx.scene.Parent
import javafx.scene.control.ProgressIndicator
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.StackPane
import javafx.scene.shape.Rectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamExtractor
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import java.awt.Point
import java.util.concurrent.atomic.AtomicReference

/**
 * Main video player node containing video-specific control.
 *
 * @constructor Creates the player with the given global objects.
 */
class VideoPlayer(
    private val streamingService: StreamingService,
    private val controller: FXMLController,
    private val windowBoundsHandler: WindowBoundsHandler,
    private val scope: CoroutineScope,
) : StackPane() {
    private companion object {
        private const val DOUBLECLICK_TIMEOUT = 250L

        private const val SKIP_TIME_MILLISECONDS = 10000L
    }

    @FXML private lateinit var videoBackgroundRectangle: Rectangle

    @FXML private lateinit var videoView: ImageView

    @FXML private lateinit var statusBar: StatusBar

    @FXML private lateinit var progress: ProgressIndicator

    private var isMouseEventDrag = false
    private val videoViewCoroutineScope = MainScope()
    private val embeddedMediaPlayer = MediaPlayerFactory().mediaPlayers().newEmbeddedMediaPlayer()
    private var resumeJob: AtomicReference<Job?> = AtomicReference(null)

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

        statusBar.scope = scope
        statusBar.embeddedMediaPlayer = embeddedMediaPlayer

        embeddedMediaPlayer.videoSurface().set(ImageViewVideoSurface(videoView))

        embeddedMediaPlayer.events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                    scope.launch { progress.setVisible(newCache != 100.toFloat()) }
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    scope.launch { windowBoundsHandler.resizeToExpanded() }
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    scope.launch { statusBar.updateVideoProgress() }
                }
            },
        )

        videoBackgroundRectangle.onMouseClicked = handler(::handleVideoAreaClicked)
        videoView.onMouseClicked = handler(::handleVideoAreaClicked)

        onMouseEntered = handler { statusBar.setExpanded(true) }
        onMouseExited = handler { statusBar.setExpanded(false) }
        onScroll =
            handler {
                if (it.deltaY < 0 && !controller.scrollControlPane(it)) {
                    windowBoundsHandler.handleScroll(it)
                    windowBoundsHandler.rejoinWindows()
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
    }

    /**
     * Updates player to show the video denoted by the given Url.
     *
     * @param[url] Url which specifies the video.
     * @return A [StreamExtractor] for the video.
     */
    public suspend fun updateVideo(url: String): StreamExtractor {
        embeddedMediaPlayer.controls().stop()
        progress.setVisible(true)

        val extractor =
            withContext(Dispatchers.IO) {
                // TODO: ParsingException
                val streamLinkHandler = streamingService.streamLHFactory.fromUrl(url)
                // TODO: ExtractionException
                val extractor = streamingService.getStreamExtractor(streamLinkHandler)
                // TODO: ExtractionException, IOException
                extractor.fetchPage()
                extractor
            }

        updateVideo(extractor)
        return extractor
    }

    /**
     * Plays a video from an existing [StreamExtractor].
     *
     * @param[extractor] Extractor which specifies the video.
     */
    public fun updateVideo(extractor: StreamExtractor) {
        embeddedMediaPlayer.controls().stop()
        progress.setVisible(true)
        videoViewCoroutineScope.coroutineContext.cancelChildren()

        // TODO: ParsingException
        statusBar.title = extractor.name

        scope.launch {
            // TODO: ExtractionException, no video stream
            val stream = extractor.videoStreams?.firstOrNull()!!
            // TODO: fail play
            embeddedMediaPlayer.media().play(stream.content)
        }
    }

    private suspend fun handleVideoAreaClicked(event: MouseEvent) {
        if (isMouseEventDrag) return

        if (event.button == MouseButton.PRIMARY) {
            val previousResumeJob = resumeJob.getAndSet(null)?.apply { cancel() }

            if (event.clickCount == 1) {
                val wasPaused = embeddedMediaPlayer.status().state() == State.PAUSED
                embeddedMediaPlayer.controls().setPause(true)

                if (wasPaused) {
                    resumeJob.set(
                        scope.launch {
                            delay(DOUBLECLICK_TIMEOUT)
                            embeddedMediaPlayer.controls().setPause(false)
                        },
                    )
                }
            }
            if (event.clickCount > 1) {
                val isForward = event.sceneX > FXMLController.BASE_WIDTH / 2
                embeddedMediaPlayer.controls().skipTime(SKIP_TIME_MILLISECONDS * (if (isForward) 1 else -1))
                if (previousResumeJob == null && event.clickCount == 2) embeddedMediaPlayer.controls().setPause(false)
                statusBar.updateVideoProgress()
            }
        }
        if (event.button == MouseButton.SECONDARY) controller.onBack()
        if (event.button == MouseButton.MIDDLE && !windowBoundsHandler.resizeToBase()) {
            windowBoundsHandler.moveToBottom(!windowBoundsHandler.horizontalDirection())
        }
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> = object : EventHandler<T> {
        override fun handle(event: T) {
            scope.launch { block(event) }
        }
    }
}

/** Operator for flipping a horizontal direction. */
operator fun HorizontalDirection.not() = if (this == HorizontalDirection.RIGHT) {
    HorizontalDirection.LEFT
} else {
    HorizontalDirection.RIGHT
}
