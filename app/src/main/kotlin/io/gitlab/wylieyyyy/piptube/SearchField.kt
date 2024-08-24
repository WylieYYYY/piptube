package io.gitlab.wylieyyyy.piptube

import javafx.beans.value.ChangeListener
import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.control.TextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory

class SearchField : TextField() {
    public lateinit var controller: FXMLController

    public lateinit var windowBoundsHandler: WindowBoundsHandler

    public lateinit var scope: CoroutineScope

    private val youtubeService =
        run {
            NewPipe.init(DownloaderImpl)
            NewPipe.getService("YouTube")
        }

    init {
        promptText = "Search"
        focusedProperty().addListener(
            ChangeListener { _, _, newValue ->
                windowBoundsHandler.focusControlPane(newValue)
            },
        )
        onAction = handler { _ -> handleSearchFieldActioned() }
    }

    private suspend fun handleSearchFieldActioned() {
        controller.controlPane.clearVideoList()
        windowBoundsHandler.focusControlPane(false)

        // TODO: ParsingException
        val searchQueryHandler =
            youtubeService.searchQHFactory.fromQuery(
                text,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                null,
            )
        scope.launch(Dispatchers.IO) {
            val extractor = youtubeService.getSearchExtractor(searchQueryHandler)
            // TODO: IOException, ExtractionException
            extractor.fetchPage()

            withContext(Dispatchers.Main) {
                // TODO: IOException, ExtractionException
                controller.controlPane.addToVideoList(extractor.getInitialPage().items)
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
