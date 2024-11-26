package io.gitlab.wylieyyyy.piptube

import io.gitlab.wylieyyyy.piptube.videolist.ChannelCard
import io.gitlab.wylieyyyy.piptube.videolist.InfoCard
import io.gitlab.wylieyyyy.piptube.videolist.SettingsPage
import io.gitlab.wylieyyyy.piptube.videolist.SubscriptionPage
import io.gitlab.wylieyyyy.piptube.videolist.VideoCard
import javafx.beans.Observable
import javafx.beans.value.ChangeListener
import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Button
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
import kotlin.getOrThrow

class VideoListGenerator(
    seenItems: List<VideoListItem> = listOf(),
    private val extractor: ListExtractor<out InfoItem>? = null,
) {
    companion object {
        private const val PAGE_SIZE = 10
    }

    sealed class VideoListItem {
        data class InfoItem<T : org.schabi.newpipe.extractor.InfoItem>(public val item: T) : VideoListItem()

        data class Node(public val node: javafx.scene.Node) : VideoListItem()
    }

    public val seenItems = seenItems.toMutableList<VideoListItem>()

    private val sentinelInitialPage = ListExtractor.InfoItemsPage(listOf(), null, listOf())
    private var currentPage: ListExtractor.InfoItemsPage<out InfoItem> = sentinelInitialPage

    public fun isProper(): Boolean {
        return extractor == null || currentPage !== sentinelInitialPage
    }

    public fun isExhausted(): Boolean {
        return extractor == null ||
            (currentPage !== sentinelInitialPage && !currentPage.hasNextPage()) ||
            currentPage === ListExtractor.InfoItemsPage.emptyPage<InfoItem>()
    }

    public suspend fun itemsFrom(index: Int): Pair<List<VideoListItem>, Boolean> {
        val items = seenItems.asSequence().drop(index).take(PAGE_SIZE).toMutableList()
        while (items.size < PAGE_SIZE && !isExhausted()) {
            items.addAll(unseenItems())
        }
        val hasNext = index + PAGE_SIZE < seenItems.size || !isExhausted()
        return Pair(items, hasNext)
    }

    public suspend fun unseenItems(): List<VideoListItem> {
        if (extractor == null) return listOf()

        return withContext(Dispatchers.IO) {
            // TODO: ExtractionException, IOException
            extractor.fetchPage()
            currentPage = nextPage()

            // TODO: ExtractionException, IOException
            val items =
                runCatching {
                    currentPage.items
                }.recoverCatching {
                    when (it) {
                        is NothingFoundException -> listOf()
                        else -> throw it
                    }
                }.getOrThrow()

            items.map(VideoListItem::InfoItem).apply {
                seenItems.addAll(this)
            }
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

    @FXML private lateinit var menuButton: Button

    @FXML private lateinit var tabList: TabPane

    @FXML private lateinit var scrollPane: ScrollPane

    @FXML private lateinit var videoList: VBox

    @FXML private lateinit var progress: ProgressIndicator

    private lateinit var subscription: Subscription
    private lateinit var subscriptionCache: SubscriptionCache

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

        menuButton.onAction =
            handler {
                val page = SettingsPage(scope, subscription)
                withClearedVideoList {
                    Pair(
                        TabIdentifier.SETTINGS,
                        VideoListGenerator(seenItems = listOf(VideoListGenerator.VideoListItem.Node(page))),
                    )
                }
            }

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

        scope.launch {
            withClearedVideoList {
                subscription = Subscription.fromStorageOrNew()
                subscriptionCache = SubscriptionCache.fromCacheOrNew()
                val page = SubscriptionPage(controller, subscription, subscriptionCache, progress)
                Pair(
                    TabIdentifier.SUBSCRIPTION,
                    VideoListGenerator(
                        seenItems =
                            listOf(VideoListGenerator.VideoListItem.Node(page)) +
                                subscriptionCache.seenItems().map(VideoListGenerator.VideoListItem::InfoItem),
                    ),
                )
            }
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

        val (identifier, generator) =
            runCatching {
                block()
            }.recoverCatching {
                tabList.setDisable(false)
                throw it
            }.getOrThrow()

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
            addToVideoList(generator)
        } else {
            tabList.selectionModel.select(targetTab)
        }
    }

    private fun clearVideoList() {
        tabList.setDisable(true)
        videoList.children.clear()
    }

    private suspend fun addToVideoList(
        generator: VideoListGenerator,
        frameIndex: Int = 0,
    ) {
        val (items, hasNext) = generator.itemsFrom(frameIndex)

        if (items.isEmpty()) {
            videoList.children.add(InfoCard("No video is available."))
            tabList.setDisable(false)
            return
        }

        videoList.children.addAll(
            items.mapNotNull {
                when (it) {
                    is VideoListGenerator.VideoListItem.Node -> it.node
                    is VideoListGenerator.VideoListItem.InfoItem<*> -> createStandardCard(it.item)
                }
            },
        )

        if (hasNext) {
            videoList.children.add(
                InfoCard("Load more...", scope) {
                    videoList.children.last().setVisible(false)
                    val index = videoList.children.lastIndex
                    addToVideoList(generator, frameIndex + items.size)
                    videoList.children.removeAt(index)
                },
            )
        }

        tabList.setDisable(false)
    }

    private fun createStandardCard(item: InfoItem): Node? {
        return when (item) {
            is ChannelInfoItem ->
                ChannelCard(item, scope, subscription, subscriptionCache) {
                    withClearedVideoList {
                        Pair(
                            TabIdentifier(TabIdentifier.TabType.CHANNEL, item.name),
                            VideoListGenerator(
                                seenItems = listOf(VideoListGenerator.VideoListItem.InfoItem(item)),
                                extractor = streamingService.getFeedExtractor(item.url),
                            ),
                        )
                    }
                }
            is StreamInfoItem ->
                VideoCard(item, scope) {
                    windowBoundsHandler.resizeToBase()
                    withClearedVideoList {
                        // TODO: ExtractionException
                        val relatedInfo =
                            controller.gotoVideoUrl(item.url)
                                .relatedItems?.items?.map(VideoListGenerator.VideoListItem::InfoItem) ?: listOf()
                        Pair(TabIdentifier.RELATED, VideoListGenerator(seenItems = relatedInfo))
                    }
                }
            else -> null
        }
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}
