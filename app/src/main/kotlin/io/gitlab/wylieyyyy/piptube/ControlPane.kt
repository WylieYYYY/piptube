package io.gitlab.wylieyyyy.piptube

import io.gitlab.wylieyyyy.piptube.videolist.ChannelCard
import io.gitlab.wylieyyyy.piptube.videolist.CommentCard
import io.gitlab.wylieyyyy.piptube.videolist.InfoCard
import io.gitlab.wylieyyyy.piptube.videolist.SettingsPage
import io.gitlab.wylieyyyy.piptube.videolist.SubscriptionPage
import io.gitlab.wylieyyyy.piptube.videolist.VideoCard
import io.gitlab.wylieyyyy.piptube.videolist.VideoListGenerator
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
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.getOrThrow

/**
 * Node containing auxiliary menu and interface outside of the player.
 *
 * @constructor Creates the control pane with the given global objects.
 */
class ControlPane(
    private val streamingService: StreamingService,
    private val controller: FXMLController,
    private val windowBoundsHandler: WindowBoundsHandler,
    private val scope: CoroutineScope,
) : VBox() {
    companion object {
        /** Radius of the floating button for switching generators. */
        public const val FLOATING_BUTTON_RADIUS = 30
    }

    @FXML private lateinit var searchField: SearchField

    @FXML private lateinit var menuButton: Button

    @FXML private lateinit var tabList: TabPane

    @FXML private lateinit var scrollPane: ScrollPane

    @FXML private lateinit var videoList: VBox

    @FXML private lateinit var floatingButton: Button

    @FXML private lateinit var progress: ProgressIndicator

    private lateinit var subscription: Subscription
    private lateinit var subscriptionCache: SubscriptionCache
    private var infiniteScrollHandler = ChangeListener<Number> { _, _, _ -> Unit }

    init {
        val loader = FXMLLoader(this::class.java.getResource("control_pane.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        floatingButton.style = (FLOATING_BUTTON_RADIUS * 2).let {
            "-fx-min-width: $it; -fx-max-height: $it"
        }

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
                windowBoundsHandler.focusControlPane(false)
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

    /**
     * Handler for a JavaFx [ScrollEvent] to scroll the video list.
     *
     * @param[event] JavaFx scroll event.
     * @return True if the control pane has been scrolled,
     *  false if the video list has reached its scroll limit.
     */
    public fun scrollVideoList(event: ScrollEvent): Boolean {
        scrollPane.vmax = videoList.height
        val oldVvalue = scrollPane.vvalue
        scrollPane.vvalue = (scrollPane.vvalue + event.deltaY).coerceIn(0.0, scrollPane.vmax)
        return oldVvalue != scrollPane.vvalue
    }

    /**
     * Clears the video list, switch to a tab if required, and set a new [VideoListGenerator].
     * Scroll will be reset even if the given generator is the same as the existing one.
     *
     * No other public methods are available to manipulate the video list,
     * this is to ensure that the video list stays in a consistent state with valid items.
     *
     * @param[block] Block to be executed to get the identifier and generator for the tab.
     *  If the identifier is identical with one that identifies an existing tab,
     *  that tab's generator will be replaced with the given one.
     */
    public suspend fun withClearedVideoList(block: suspend () -> Pair<TabIdentifier, VideoListGenerator>) {
        scrollPane.vvalueProperty().removeListener(infiniteScrollHandler)
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

    private suspend fun addToVideoList(generator: VideoListGenerator, frameIndex: Int = 0) {
        scrollPane.vvalueProperty().removeListener(infiniteScrollHandler)
        val (items, hasNext) = generator.itemsFrom(frameIndex)

        val oldScrollVvalue = scrollPane.vvalue

        videoList.children.addAll(
            items.mapNotNull {
                when (it) {
                    is VideoListGenerator.VideoListItem.Node -> it.node
                    is VideoListGenerator.VideoListItem.InfoItem<*> -> createStandardCard(it.item)
                }
            },
        )

        if (hasNext) {
            infiniteScrollHandler =
                ChangeListener { property, _, newValue ->
                    scope.launch {
                        if (newValue as Double > scrollPane.vmax - VideoCard.HEIGHT * 2) {
                            property.removeListener(infiniteScrollHandler)
                            addToVideoList(generator, frameIndex + items.size)
                        }
                    }
                }
            scrollPane.vvalueProperty().addListener(infiniteScrollHandler)
        }

        videoList.autosize()
        scrollPane.vmax = videoList.height
        scrollPane.vvalue = oldScrollVvalue

        tabList.setDisable(false)
    }

    private fun createStandardCard(item: InfoItem): Node? = when (item) {
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
        is CommentsInfoItem ->
            CommentCard(item)
        is StreamInfoItem ->
            VideoCard(item, scope) {
                windowBoundsHandler.resizeToBase()
                withClearedVideoList {
                    // TODO: ExtractionException
                    val relatedInfo =
                        controller.gotoVideoUrl(item.url)
                            .relatedItems?.items?.map(VideoListGenerator.VideoListItem::InfoItem) ?: listOf()

                    val relatedVideosPageFactory = { card: InfoCard ->
                        val seenItems = listOf(VideoListGenerator.VideoListItem.Node(card)) + relatedInfo
                        Pair(TabIdentifier.RELATED, VideoListGenerator(seenItems))
                    }

                    val switchToCommentsCard = InfoCard("Switch to comments", scope) { switchToCommentsCard: InfoCard ->
                        withClearedVideoList {
                            val switchToVideosCard = InfoCard("Switch to videos", scope) {
                                withClearedVideoList { relatedVideosPageFactory(switchToCommentsCard) }
                            }

                            // TODO: ParsingException
                            val commentLinkHandler = streamingService.commentsLHFactory.fromUrl(item.url)
                            val generator = VideoListGenerator(
                                seenItems = listOf(VideoListGenerator.VideoListItem.Node(switchToVideosCard)),
                                // TODO: ExtractionException
                                extractor = streamingService.getCommentsExtractor(commentLinkHandler),
                            )
                            Pair(TabIdentifier.RELATED, generator)
                        }
                    }

                    relatedVideosPageFactory(switchToCommentsCard)
                }
            }
        else -> null
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> = object : EventHandler<T> {
        override fun handle(event: T) {
            scope.launch { block(event) }
        }
    }
}
