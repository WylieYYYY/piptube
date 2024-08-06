package io.gitlab.wylieyyyy.piptube

import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.layout.VBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.util.Stack
import javax.swing.JFrame

class FXMLController(private val frame: JFrame) {
    companion object {
        public const val BASE_WIDTH = 640

        public const val BASE_HEIGHT = 360
    }

    public val parent: Parent

    @FXML private lateinit var mainBox: VBox

    private lateinit var controlPane: ControlPane

    private lateinit var player: VideoPlayer

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
        player = VideoPlayer(this, frame, windowBoundsHandler, scope)
        controlPane = ControlPane(this, player, frame, windowBoundsHandler, scope)
        mainBox.children.addAll(controlPane, player)
        windowBoundsHandler.moveToBottomRight()
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
