package io.gitlab.wylieyyyy.piptube

import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import kotlinx.coroutines.MainScope
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.util.Stack
import javax.swing.JFrame
import javax.swing.JWindow

class FXMLController(private val controlFrame: JFrame, private val videoWindow: JWindow) {
    companion object {
        public const val BASE_WIDTH = 640

        public const val BASE_HEIGHT = 360
    }

    public val controlPane: ControlPane

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

        windowBoundsHandler.moveToBottomRight()
        controlFrame.setVisible(true)
        videoWindow.setVisible(true)
    }

    public suspend fun gotoVideoUrl(url: String): StreamExtractor {
        return videoStack.push(player.updateVideo(url))
    }

    public suspend fun onBack() {
        val lastVideo = videoStack.pop()
        if (videoStack.empty()) {
            videoStack.push(lastVideo)
        } else {
            val extractor = videoStack.peek()
            controlPane.clearVideoList()
            player.updateVideo(extractor)
            // TODO: ExtractionException
            controlPane.addToVideoList(
                TabIdentifier.RELATED,
                extractor.relatedItems?.items ?: listOf(),
            )
        }
    }
}
