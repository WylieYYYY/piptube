package io.gitlab.wylieyyyy.piptube

import javafx.beans.value.ChangeListener
import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.control.TextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.StreamingService

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
        controller.controlPane.withClearedVideoList {
            windowBoundsHandler.focusControlPane(false)

            // TODO: ParsingException
            val searchQueryHandler = streamingService.searchQHFactory.fromQuery(text, listOf(), null)
            val extractor = streamingService.getSearchExtractor(searchQueryHandler)
            Pair(
                TabIdentifier(TabIdentifier.TabType.SEARCH, text),
                VideoListGenerator(extractor = extractor),
            )
        }
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}
