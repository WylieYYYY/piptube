package io.gitlab.wylieyyyy.piptube

import io.gitlab.wylieyyyy.piptube.videolist.ChannelCard
import io.gitlab.wylieyyyy.piptube.videolist.InfoCard
import io.gitlab.wylieyyyy.piptube.videolist.VideoCard
import javafx.beans.Observable
import javafx.beans.value.ChangeListener
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.collections.mutableListOf

class VideoListGenerator(
    public val seenItems: MutableList<InfoItem> = mutableListOf(),
    private val extractor: ListExtractor<out InfoItem>? = null,
) {
    private val sentinelInitialPage = ListExtractor.InfoItemsPage(listOf(), null, listOf())
    private var currentPage: ListExtractor.InfoItemsPage<out InfoItem> = sentinelInitialPage

    public fun isProper(): Boolean {
        return extractor == null || currentPage !== sentinelInitialPage
    }

    public fun isExhausted() = currentPage === ListExtractor.InfoItemsPage.emptyPage<InfoItem>()

    public suspend fun unseenItems(): List<InfoItem> {
        if (extractor == null) return listOf()

        return withContext(Dispatchers.IO) {
            // TODO: ExtractionException, IOException
            extractor.fetchPage()
            currentPage = nextPage()
            // TODO: ExtractionException, IOException
            runCatching {
                seenItems.addAll(currentPage.items)
                currentPage.items
            }.recoverCatching {
                when (it) {
                    is NothingFoundException -> listOf()
                    else -> throw it
                }
            }.getOrThrow()
        }
    }

    private fun nextPage(): ListExtractor.InfoItemsPage<out InfoItem> {
        return currentPage.let {
            (
                if (it === sentinelInitialPage) {
                    extractor?.initialPage
                } else if (it.hasNextPage()) {
                    extractor?.getPage(it.nextPage)
                } else {
                    null
                }
            ) ?: ListExtractor.InfoItemsPage.emptyPage()
        }
    }
}

class ControlPane(
    private val streamingService: StreamingService,
    private val controller: FXMLController,
    private val windowBoundsHandler: WindowBoundsHandler,
    private val scope: CoroutineScope,
) : VBox() {
    @FXML private lateinit var searchField: SearchField

    @FXML private lateinit var tabList: TabPane

    @FXML private lateinit var scrollPane: ScrollPane

    @FXML private lateinit var videoList: VBox

    @FXML private lateinit var progress: ProgressIndicator

    init {
        val loader = FXMLLoader(this::class.java.getResource("control_pane.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        searchField.streamingService = streamingService
        searchField.controller = controller
        searchField.windowBoundsHandler = windowBoundsHandler
        searchField.scope = scope

        tabList.selectionModel.selectedItemProperty().addListener(
            ChangeListener { _, _, newValue ->
                clearVideoList()
                scope.launch {
                    if (newValue == null) {
                        addToVideoList(VideoListGenerator())
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val generator = newValue.userData as VideoListGenerator
                        addToVideoList(generator)
                    }
                }
            },
        )

        videoList.children.addListener { _: Observable ->
            progress.setVisible(videoList.children.isEmpty())
        }
    }

    public fun scrollVideoList(event: ScrollEvent): Boolean {
        scrollPane.vmax = videoList.height
        val oldVvalue = scrollPane.vvalue
        scrollPane.vvalue = (scrollPane.vvalue + event.deltaY).coerceIn(0.0, scrollPane.vmax)
        return oldVvalue != scrollPane.vvalue
    }

    public suspend fun withClearedVideoList(block: suspend () -> Pair<TabIdentifier, VideoListGenerator>) {
        clearVideoList()
        AutoCloseable { tabList.setDisable(false) }.use {
            val (identifier, generator) = block()

            val matchedTab = tabList.tabs.firstOrNull { it.id == identifier.toString() }
            val targetTab =
                matchedTab ?: Tab().apply {
                    userData = generator
                    id = identifier.toString()
                    text = identifier.toString()
                    tabList.tabs.add(this)
                }

            targetTab.userData = generator
            if (tabList.selectionModel.selectedItem == matchedTab) {
                clearVideoList()
                addToVideoList(generator)
            } else {
                tabList.selectionModel.select(targetTab)
            }
        }
    }

    private fun clearVideoList() {
        tabList.setDisable(true)
        videoList.children.clear()
    }

    private suspend fun addToVideoList(
        generator: VideoListGenerator,
        unseenOnly: Boolean = false,
    ) {
        tabList.setDisable(false)

        val items =
            if (generator.isProper() && !unseenOnly) {
                generator.seenItems
            } else {
                val unseenItems = generator.unseenItems()
                if (unseenOnly) unseenItems else generator.seenItems
            }

        if (items.isEmpty()) {
            videoList.children.add(InfoCard("No video is available."))
            return
        }

        videoList.children.addAll(
            items.mapNotNull {
                when (it) {
                    is ChannelInfoItem ->
                        ChannelCard(it, scope) {
                            withClearedVideoList {
                                Pair(
                                    TabIdentifier(TabIdentifier.TabType.CHANNEL, it.name),
                                    VideoListGenerator(mutableListOf(it), streamingService.getFeedExtractor(it.url)),
                                )
                            }
                        }
                    is StreamInfoItem ->
                        VideoCard(it, scope) {
                            windowBoundsHandler.resizeToBase()
                            withClearedVideoList {
                                // TODO: ExtractionException
                                val relatedInfo =
                                    controller.gotoVideoUrl(it.url)
                                        .relatedItems?.items?.toMutableList() ?: mutableListOf()
                                Pair(TabIdentifier.RELATED, VideoListGenerator(relatedInfo))
                            }
                        }
                    else -> null
                }
            },
        )

        if (generator.isExhausted()) {
            videoList.children.add(InfoCard("No video is available."))
            return
        }

        videoList.children.add(
            InfoCard("Load more...", scope) {
                videoList.children.last().setVisible(false)
                val index = videoList.children.lastIndex
                addToVideoList(generator, true)
                videoList.children.removeAt(index)
            },
        )
    }
}
