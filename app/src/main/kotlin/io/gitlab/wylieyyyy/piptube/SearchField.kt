package io.gitlab.wylieyyyy.piptube

import javafx.beans.value.ChangeListener
import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.control.TextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException
import kotlin.getOrThrow

class SearchField : TextField() {
    public lateinit var streamingService: StreamingService

    public lateinit var controller: FXMLController

    public lateinit var windowBoundsHandler: WindowBoundsHandler

    public lateinit var scope: CoroutineScope

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
        val searchQueryHandler = streamingService.searchQHFactory.fromQuery(text, listOf(), null)
        scope.launch(Dispatchers.IO) {
            val extractor = streamingService.getSearchExtractor(searchQueryHandler)
            // TODO: IOException, ExtractionException
            extractor.fetchPage()

            withContext(Dispatchers.Main) {
                // TODO: IOException, ExtractionException
                val items =
                    runCatching { extractor.initialPage.items }.recoverCatching {
                        when (it) {
                            is NothingFoundException -> listOf()
                            else -> throw it
                        }
                    }.getOrThrow()
                controller.controlPane.addToVideoList(TabIdentifier(TabIdentifier.TabType.SEARCH, text), items)
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
