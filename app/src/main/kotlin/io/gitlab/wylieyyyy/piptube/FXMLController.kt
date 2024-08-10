package io.gitlab.wylieyyyy.piptube

import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val controlPane: ControlPane

    private val player: VideoPlayer

    private val scope = MainScope()
    private val youtubeService =
        run {
            NewPipe.init(DownloaderImpl)
            NewPipe.getService("YouTube")
        }
    private val videoStack = Stack<StreamExtractor>()
    private val windowBoundsHandler = WindowBoundsHandler(controlFrame, videoWindow, BASE_HEIGHT)

    init {
        player = VideoPlayer(this, windowBoundsHandler, scope)
        controlPane = ControlPane(this, player, controlFrame, windowBoundsHandler, scope)

        for ((container, parent) in mapOf(controlFrame to controlPane, videoWindow to player)) {
            val components = container.contentPane.components
            (components.single() as JFXPanel).scene = Scene(parent)
        }

        controlFrame.setVisible(true)
        windowBoundsHandler.moveToBottomRight()
        videoWindow.setVisible(true)
    }

    public suspend fun gotoVideoUrl(url: String): StreamExtractor {
        return videoStack.push(updateVideo(url))
    }

    public suspend fun onBack() {
        val lastVideo = videoStack.pop()
        if (videoStack.empty()) {
            videoStack.push(lastVideo)
        } else {
            player.updateVideo(videoStack.peek())
        }
    }

    private suspend fun updateVideo(url: String): StreamExtractor {
        player.disposeMedia()
        controlPane.clearVideoList()

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
            withContext(Dispatchers.Main) { player.updateVideo(extractor) }
        }
        return withContext(Dispatchers.IO) { deferredExtractor.await() }
    }
}
