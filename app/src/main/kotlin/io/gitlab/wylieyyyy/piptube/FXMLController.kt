package io.gitlab.wylieyyyy.piptube

import javafx.beans.value.ChangeListener
import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
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

    @FXML private lateinit var mainBox: VBox

    @FXML private lateinit var searchField: TextField

    @FXML private lateinit var videoList: VBox

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
        player = VideoPlayer(this, frame, windowBoundsHandler, scope)
        mainBox.children.add(player)
        windowBoundsHandler.moveToBottomRight()
    }

    public fun clearVideoList() {
        videoList.children.clear()
    }

    public suspend fun addToVideoList(items: List<InfoItem>) {
        videoList.children.addAll(
            items.filterIsInstance<StreamInfoItem>().map {
                VideoListEntryControl(it) {
                    scope.launch {
                        windowBoundsHandler.resizeToBase()
                        videoStack.push(updateVideo(it.url))
                    }
                }
            },
        )
    }

    private suspend fun onSearchFieldActioned() {
        videoList.children.clear()
        player.requestFocus()
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
                // TODO: IOException, ExtractionException
                addToVideoList(extractor.getInitialPage().items)
            }
        }
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
            withContext(Dispatchers.Main) { player.updateVideo(extractor) }
        }
        return withContext(Dispatchers.IO) { deferredExtractor.await() }
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}
