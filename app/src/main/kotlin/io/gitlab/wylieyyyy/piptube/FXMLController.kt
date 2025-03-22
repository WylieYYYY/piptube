package io.gitlab.wylieyyyy.piptube

import io.gitlab.wylieyyyy.piptube.videolist.VideoListGenerator
import javafx.embed.swing.JFXPanel
import javafx.geometry.HorizontalDirection
import javafx.scene.Scene
import javafx.scene.input.ScrollEvent
import kotlinx.coroutines.MainScope
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.util.Stack
import javax.swing.JFrame
import javax.swing.JWindow

/**
 * JavaFx controller, all passed windows should contain a [JFXPanel].
 * This controller contains state information that is global.
 *
 * @param[controlFrame] Top control pane window.
 * @param[videoWindow] Bottom video player window.
 * @constructor Creates the controller for this application.
 */
class FXMLController(private val controlFrame: JFrame, private val videoWindow: JWindow) {
    /** Predefined dimensional constants. */
    companion object {
        /** Common width for both windows. */
        public const val BASE_WIDTH = 640

        /** Common height for both windows. */
        public const val BASE_HEIGHT = 360
    }

    /** Control pane, exposed for interactions that do not involve global states. */
    public val controlPane: ControlPane

    /** Video player, exposed for interactions that do not involve global states. */
    public val player: VideoPlayer

    private val scope = MainScope()
    private val youtubeService =
        run {
            NewPipe.init(DownloaderImpl)
            NewPipe.getService("YouTube")
        }
    private val videoStack = Stack<StreamExtractor>()
    private val windowBoundsHandler = WindowBoundsHandler(controlFrame, videoWindow, BASE_HEIGHT)

    init {
        controlPane = ControlPane(youtubeService, this, windowBoundsHandler, scope)
        player = VideoPlayer(youtubeService, this, windowBoundsHandler, scope)

        for ((container, parent) in mapOf(controlFrame to controlPane, videoWindow to player)) {
            val components = container.contentPane.components
            (components.single() as JFXPanel).scene = Scene(parent)
            container.pack()
        }

        val controlVerticalInset = controlFrame.insets.top + controlFrame.insets.bottom
        controlFrame.setLocation(videoWindow.location.x, videoWindow.location.y - controlVerticalInset - BASE_HEIGHT)
        windowBoundsHandler.moveToBottom(HorizontalDirection.RIGHT)

        controlFrame.setVisible(true)
        videoWindow.setVisible(true)
    }

    /**
     * Alias for scrolling video list within the control pane.
     * This exists as scrolling the video list is implementation detail of the control pane.
     *
     * @param[event] JavaFx scroll event.
     * @return True if the control pane has been scrolled,
     *  false if the control pane has reached its scroll limit.
     */
    public fun scrollControlPane(event: ScrollEvent) = controlPane.scrollVideoList(event)

    /**
     * Goes to the video denoted by the given Url.
     * This interacts with global state as it interacts with the stack of videos.
     *
     * @param[url] Url which specifies the video.
     * @return A [StreamExtractor] for the video, this is for extracting related items.
     */
    public suspend fun gotoVideoUrl(url: String): StreamExtractor = videoStack.push(player.updateVideo(url))

    /**
     * Navigates backward one video.
     * This interacts with global state as it interacts with the stack of videos.
     */
    public suspend fun onBack() {
        val lastVideo = videoStack.pop()
        if (videoStack.empty()) {
            videoStack.push(lastVideo)
        } else {
            val extractor = videoStack.peek()
            controlPane.withClearedVideoList {
                player.updateVideo(extractor)
                // TODO: ExtractionException
                Pair(
                    TabIdentifier.RELATED,
                    VideoListGenerator(
                        seenItems =
                        extractor.relatedItems?.items
                            ?.map(VideoListGenerator.VideoListItem::InfoItem) ?: listOf(),
                    ),
                )
            }
        }
    }
}
