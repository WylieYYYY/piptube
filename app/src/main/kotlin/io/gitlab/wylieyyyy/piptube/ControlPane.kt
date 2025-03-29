package io.gitlab.wylieyyyy.piptube

import io.gitlab.wylieyyyy.piptube.videolist.ChannelCard
import io.gitlab.wylieyyyy.piptube.videolist.CommentCard
import io.gitlab.wylieyyyy.piptube.videolist.GeneratorTab
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        /** Padding of the floating button from the bottom right. */
        public const val FLOATING_BUTTON_PADDING = 10

        /** Radius of the floating button for switching generators. */
        public const val FLOATING_BUTTON_RADIUS = 30

        private const val VMAX_SETTLE_DELAY_MILLISECONDS = 50L
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

        menuButton.onAction = handler { handleMenuButtonActioned() }

        tabList.selectionModel.selectedItemProperty().addListener(
            ChangeListener { _, _, newValue ->
                windowBoundsHandler.focusControlPane(false)
                clearVideoList()
                scope.launch {
                    if (newValue == null) {
                        addToVideoList(VideoListGenerator(), false)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val generatorTab = newValue.userData as GeneratorTab
                        addToVideoList(
                            generatorTab.primaryGenerator,
                            generatorTab.isSecondaryGeneratorProvided(),
                        )
                    }
                }
            },
        )

        videoList.children.addListener { _: Observable ->
            progress.setVisible(videoList.children.isEmpty())
        }

        floatingButton.onAction =
            handler {
                @Suppress("UNCHECKED_CAST")
                val generatorTab = tabList.selectionModel.selectedItem.userData as GeneratorTab
                withClearedVideoList {
                    generatorTab.switchGenerators().also {
                        tabList.selectionModel.selectedItem.userData = it
                    }
                }
            }

        scope.launch {
            withClearedVideoList {
                subscription = Subscription.fromStorageOrNew()
                subscriptionCache = SubscriptionCache.fromCacheOrNew()
                val page = SubscriptionPage(controller, subscription, subscriptionCache, progress)
                GeneratorTab(
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
     * @param[block] Block to be executed to get the generator tab.
     *  If the identifier is identical with one that identifies an existing tab,
     *  that tab's generator will be replaced with the given one.
     */
    public suspend fun withClearedVideoList(block: suspend () -> GeneratorTab) {
        scrollPane.vvalueProperty().removeListener(infiniteScrollHandler)
        clearVideoList()

        val generatorTab =
            runCatching {
                block()
            }.recoverCatching {
                tabList.setDisable(false)
                throw it
            }.getOrThrow()

        val matchedTab = tabList.tabs.firstOrNull {
            it.id == generatorTab.identifier.toString()
        }
        val targetTab =
            matchedTab ?: Tab().apply {
                userData = generatorTab
                id = generatorTab.identifier.toString()
                text = generatorTab.identifier.toString()
                tabList.tabs.add(this)
            }

        targetTab.userData = generatorTab
        if (tabList.selectionModel.selectedItem == matchedTab) {
            addToVideoList(
                generatorTab.primaryGenerator,
                generatorTab.isSecondaryGeneratorProvided(),
            )
        } else {
            tabList.selectionModel.select(targetTab)
        }
    }

    private suspend fun handleMenuButtonActioned() {
        val page = SettingsPage(scope, subscription)
        withClearedVideoList {
            GeneratorTab(
                TabIdentifier.SETTINGS,
                VideoListGenerator(seenItems = listOf(VideoListGenerator.VideoListItem.Node(page))),
            )
        }
    }

    private fun clearVideoList() {
        tabList.setDisable(true)
        videoList.children.clear()
        floatingButton.setVisible(false)
    }

    private suspend fun addToVideoList(
        generator: VideoListGenerator,
        shouldDisplayFloatingButton: Boolean,
        frameIndex: Int = 0,
    ) {
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
                            addToVideoList(generator, shouldDisplayFloatingButton, frameIndex + items.size)
                        }
                    }
                }
            scrollPane.vvalueProperty().addListener(infiniteScrollHandler)
        }

        videoList.autosize()
        val newScrollVvalue =
            calculateNewScrollVvalue(scrollPane.height, oldScrollVvalue, scrollPane.vmax, videoList.height)
        scrollPane.vmax = videoList.height

        scrollPane.setDisable(true)
        scope.launch(Dispatchers.Default) {
            delay(VMAX_SETTLE_DELAY_MILLISECONDS)
            withContext(Dispatchers.Main) {
                scrollPane.vvalue = newScrollVvalue
                scrollPane.setDisable(false)
            }
        }

        tabList.setDisable(false)
        floatingButton.setVisible(shouldDisplayFloatingButton)
    }

    private fun createStandardCard(item: InfoItem): Node? = when (item) {
        is ChannelInfoItem ->
            ChannelCard(item, scope, subscription, subscriptionCache) {
                withClearedVideoList {
                    GeneratorTab(
                        TabIdentifier(TabIdentifier.TabType.CHANNEL, item.name),
                        VideoListGenerator(
                            seenItems = listOf(VideoListGenerator.VideoListItem.InfoItem(item)),
                            extractor = streamingService.getFeedExtractor(item.url),
                        ),
                    )
                }
            }
        is CommentsInfoItem ->
            CommentCard(item, scope)
        is StreamInfoItem ->
            VideoCard(item, scope) {
                windowBoundsHandler.resizeToBase()
                withClearedVideoList {
                    // TODO: ExtractionException
                    val relatedInfo =
                        controller.gotoVideoUrl(item.url)
                            .relatedItems?.items?.map(VideoListGenerator.VideoListItem::InfoItem) ?: listOf()
                    val relatedGenerator = VideoListGenerator(seenItems = relatedInfo)

                    // TODO: ParsingException
                    val commentLinkHandler = streamingService.commentsLHFactory.fromUrl(item.url)
                    val commentGenerator = VideoListGenerator(
                        // TODO: ExtractionException
                        extractor = streamingService.getCommentsExtractor(commentLinkHandler),
                    )

                    GeneratorTab(TabIdentifier.RELATED, relatedGenerator, commentGenerator)
                }
            }
        else -> null
    }

    private fun calculateNewScrollVvalue(
        scrollPaneHeight: Double,
        oldVvalue: Double,
        oldVmax: Double,
        newVmax: Double,
    ): Double {
        val denominator = oldVmax * (newVmax - scrollPaneHeight)

        if (denominator == 0.0) {
            return oldVvalue
        }

        // VVALUE[new] - (VVALUE[new] / VMAX[new]) * HEIGHT = VVALUE[old] - (VVALUE[old] / VMAX[old]) * HEIGHT
        return (oldVvalue * newVmax * (oldVmax - scrollPaneHeight)) / denominator
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> = object : EventHandler<T> {
        override fun handle(event: T) {
            scope.launch { block(event) }
        }
    }
}
