package io.gitlab.wylieyyyy.piptube

import javafx.beans.Observable
import javafx.beans.value.ChangeListener
import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.swing.JFrame

class ControlPane(
    private val controller: FXMLController,
    private val frame: JFrame,
    private val windowBoundsHandler: WindowBoundsHandler,
    private val scope: CoroutineScope,
) : VBox() {
    @FXML private lateinit var searchField: TextField

    @FXML private lateinit var videoList: VBox

    @FXML private lateinit var progress: ProgressIndicator

    private val youtubeService =
        run {
            NewPipe.init(DownloaderImpl)
            NewPipe.getService("YouTube")
        }

    init {
        val loader = FXMLLoader(this::class.java.getResource("control_pane.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
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
        searchField.onAction = handler { _ -> handleSearchFieldActioned() }
        videoList.children.addListener { _: Observable ->
            progress.setVisible(videoList.children.isEmpty())
        }
    }

    public fun clearVideoList() {
        videoList.children.clear()
    }

    private suspend fun addToVideoList(items: List<InfoItem>) {
        videoList.children.addAll(
            items.filterIsInstance<StreamInfoItem>().map {
                VideoListEntryControl(it) {
                    scope.launch {
                        windowBoundsHandler.resizeToBase()
                        videoList.children.clear()
                        // TODO: ExtractionException
                        val relatedInfo = controller.gotoVideoUrl(it.url).relatedItems?.items ?: listOf()
                        addToVideoList(relatedInfo)
                    }
                }
            },
        )
    }

    private suspend fun handleSearchFieldActioned() {
        videoList.children.clear()
        progress.setVisible(true)
        controller.player.requestFocus()

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

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}
