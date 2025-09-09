package io.gitlab.wylieyyyy.piptube

import io.gitlab.wylieyyyy.piptube.videolist.GeneratorTab
import io.gitlab.wylieyyyy.piptube.videolist.VideoListGenerator
import javafx.beans.value.ChangeListener
import javafx.event.Event
import javafx.event.EventHandler
import javafx.geometry.Side
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.TextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.StreamingService

class SearchField : TextField() {
    private companion object {
        private const val SUGGESTION_DEBOUNCE_MILLISECONDS = 400L

        private const val SUGGESTION_MINIMUM_TEXT_LENGTH = 3
    }

    public lateinit var streamingService: StreamingService

    public lateinit var controller: FXMLController

    public lateinit var windowBoundsHandler: WindowBoundsHandler

    public lateinit var scope: CoroutineScope

    private val suggestionMenu = ContextMenu()

    private var suggestionJob: Job? = null

    init {
        promptText = "Search"
        focusedProperty().addListener(
            ChangeListener { _, _, newValue ->
                windowBoundsHandler.focusControlPane(newValue)
            },
        )
        onAction = handler { _ ->
            handleSearchFieldActioned()
        }

        onKeyTyped = handler { _ ->
            suggestionJob?.cancel()
            suggestionJob = scope.launch {
                delay(SUGGESTION_DEBOUNCE_MILLISECONDS)

                if (text.length < SUGGESTION_MINIMUM_TEXT_LENGTH) {
                    suggestionMenu.hide()
                    return@launch
                }

                // TODO: IOException, ExtractionException
                val suggestions = streamingService.suggestionExtractor.suggestionList(text).map {
                    val item = MenuItem(it)
                    item.onAction = handler { _ ->
                        this@SearchField.text = it
                        handleSearchFieldActioned()
                    }
                    item
                }

                if (suggestions.isEmpty()) {
                    suggestionMenu.hide()
                } else {
                    suggestionMenu.items.setAll(suggestions)
                    suggestionMenu.show(this@SearchField, Side.BOTTOM, 0.0, 0.0)
                }
            }
        }

        contextMenu = suggestionMenu
    }

    private suspend fun handleSearchFieldActioned() {
        controller.controlPane.withClearedVideoList {
            windowBoundsHandler.focusControlPane(false)

            // TODO: ParsingException
            val searchQueryHandler = streamingService.searchQHFactory.fromQuery(text, listOf(), null)
            // TODO: ExtractionException
            val extractor = streamingService.getSearchExtractor(searchQueryHandler)
            GeneratorTab(
                TabIdentifier(TabIdentifier.TabType.SEARCH, text),
                VideoListGenerator(extractor = extractor),
            )
        }
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> = object : EventHandler<T> {
        override fun handle(event: T) {
            scope.launch { block(event) }
        }
    }
}
