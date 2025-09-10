package io.gitlab.wylieyyyy.piptube.videolist

import io.gitlab.wylieyyyy.piptube.TabIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.produceIn
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException
import org.schabi.newpipe.extractor.stream.StreamExtractor

/**
 * Tab that consists of one or two generators.
 * If two generators are specified, then the user is able to navigate between the two using controls.
 *
 * @property[identifier] Identifier for the tab. It should be generic over the generators since it is shared.
 * @property[primaryGenerator] Generator that will be displayed to the user first without any intervention.
 * @property[secondaryGenerator] Generator that the user can switch to using controls,
 *  default is a sentinel generator which signals that no switching is allowed.
 * @constructor Creates a tab with the given identifier that can contain the specified generators.
 */
data class GeneratorTab(
    public val identifier: TabIdentifier,
    public val primaryGenerator: VideoListGenerator,
    public val secondaryGenerator: VideoListGenerator = SENTINEL_NO_SWITCHING_GENERATOR,
) {
    companion object {
        /**
         * Creates a related tab from an extractor.
         * It includes related streams as primary, and comments as secondary.
         *
         * @param[extractor] Extractor to get items from, its page must be fetched.
         * @return A new tab with the predefined related identifier.
         */
        public fun createRelated(extractor: StreamExtractor): GeneratorTab {
            // TODO: ExtractionException
            val relatedInfo = extractor.relatedItems?.items?.map(VideoListGenerator.VideoListItem::InfoItem) ?: listOf()
            val relatedGenerator = VideoListGenerator(seenItems = relatedInfo, dynamicFlow = null)

            // TODO: ParsingException
            val commentLinkHandler = extractor.service.commentsLHFactory.fromUrl(extractor.url)
            val commentGenerator = VideoListGenerator(
                // TODO: ExtractionException
                extractor = extractor.service.getCommentsExtractor(commentLinkHandler),
            )

            return GeneratorTab(TabIdentifier.RELATED, relatedGenerator, commentGenerator)
        }

        private val SENTINEL_NO_SWITCHING_GENERATOR = VideoListGenerator(dynamicFlow = null)
    }

    /**
     * Checks if the secondary generator is provided and can be switched to.
     *
     * @return True if the secondary generator is provided, false otherwise.
     */
    public fun isSecondaryGeneratorProvided() = secondaryGenerator !== SENTINEL_NO_SWITCHING_GENERATOR

    /**
     * Gets a new instance of [GeneratorTab] with the primary and secondary generator switched.
     * Only allowed if a secondary generator is provided.
     *
     * @return A new instance with the generators switched.
     */
    public fun switchGenerators(): GeneratorTab {
        if (!isSecondaryGeneratorProvided()) {
            error("Generator switching is only allowed if a secondary generator is provided")
        }
        return GeneratorTab(identifier, secondaryGenerator, primaryGenerator)
    }
}

/**
 * Generator for video list items.
 * This allows prepended static items and buffers dynamic items from an extractor.
 * Items can be randomly accessed by item index rather than dealing with pages.
 *
 * @param[seenItems] List of static video list items, the default value is an empty list.
 * @param[dynamicFlow] Flow to fetch dynamic items from, null if no dynamic items are required.
 *  The default value is null.
 * @constructor Creates a generator with the given static and dynamic items.
 */
class VideoListGenerator(seenItems: List<VideoListItem> = listOf(), dynamicFlow: Flow<InfoItem>? = null) {
    private companion object {
        private const val PAGE_SIZE = 10
    }

    /** Video list items that are either JavaFx nodes or NewPipe info items. */
    sealed class VideoListItem {
        /** Variant that wraps a NewPipe [org.schabi.newpipe.extractor.InfoItem]. */
        data class InfoItem<T : org.schabi.newpipe.extractor.InfoItem>(public val item: T) : VideoListItem()

        /** Variant that wraps a JavaFx [javafx.scene.Node]. */
        data class Node(public val node: javafx.scene.Node) : VideoListItem()
    }

    /**
     * List of items that are available now.
     * Initially equivalent to the list of static items, with more dynamic items added after fetching.
     */
    public val seenItems = seenItems.toMutableList<VideoListItem>()

    private val dynamicChannel = dynamicFlow?.produceIn(MainScope())
    private var isFlowQueried = false
    private var isFlowExhausted = false

    /**
     * Creates a generator with the given static and dynamic items.
     *
     * @param[seenItems] List of static video list items, the default value is an empty list.
     * @param[extractor] Extractor to fetch dynamic items from, null if no dynamic items are required.
     *  The default value is null.
     */
    public constructor(
        seenItems: List<VideoListItem> = listOf(),
        extractor: ListExtractor<out InfoItem>? = null,
    ) : this(seenItems, extractor?.asFlow())

    /**
     * Checks whether this generator is proper.
     * Proper here means that either this generator contains only static items,
     * or some dynamic items have been fetched.
     *
     * @return True if this generator is proper by this definition, false otherwise.
     */
    public fun isProper(): Boolean = dynamicChannel == null || isFlowQueried

    /**
     * Checks whether this generator has fetched all dynamic items.
     *
     * @return True if the generator contains only static items or all dynamic items have been
     *  fetched, false otherwise.
     */
    public fun isExhausted(): Boolean = dynamicChannel == null || isFlowExhausted

    /**
     * Random accessor for items in this generator, fetch if necessary.
     * Page size is determined internally.
     *
     * @param[index] Index of the starting item.
     * @return A pair of a list of [VideoListItem] starting at the given item index,
     *  and a boolean of whether there are more items after this list that can be accessed.
     */
    public suspend fun itemsFrom(index: Int): Pair<List<VideoListItem>, Boolean> {
        val items = seenItems.asSequence().drop(index).take(PAGE_SIZE).toMutableList()
        while (items.size < PAGE_SIZE && !isExhausted()) {
            isFlowQueried = true

            try {
                val unseenItem = VideoListItem.InfoItem(dynamicChannel!!.receive())
                seenItems.add(unseenItem)
                items.add(unseenItem)
            } catch (_: ClosedReceiveChannelException) {
                isFlowExhausted = true
            }
        }
        val hasNext = index + PAGE_SIZE < seenItems.size || !isExhausted()
        return Pair(items, hasNext)
    }
}

/**
 * Creates a flow that emits info items from the given extractor.
 *
 * @receiver Extractor to get items from.
 * @return The flow that emits the extracted info items.
 */
fun ListExtractor<out InfoItem>.asFlow() = flow<InfoItem> {
    val sentinelInitialPage = ListExtractor.InfoItemsPage(listOf(), null, listOf())
    var currentPage: ListExtractor.InfoItemsPage<out InfoItem> = sentinelInitialPage

    fun nextPage(): ListExtractor.InfoItemsPage<out InfoItem> = currentPage.let {
        (
            if (it === sentinelInitialPage) {
                initialPage
            } else if (it.hasNextPage()) {
                getPage(it.nextPage)
            } else {
                null
            }
            ) ?: ListExtractor.InfoItemsPage.emptyPage()
    }

    while ((currentPage === sentinelInitialPage || currentPage.hasNextPage()) &&
        currentPage !== ListExtractor.InfoItemsPage.emptyPage<InfoItem>()
    ) {
        // TODO: ExtractionException, IOException
        fetchPage()

        // TODO: ExtractionException, IOException
        val items =
            runCatching {
                currentPage = nextPage()
                currentPage.items.toTypedArray()
            }.recoverCatching {
                when (it) {
                    is NothingFoundException -> arrayOf()
                    else -> throw it
                }
            }.getOrThrow()

        for (item in items) emit(item)
    }
}.flowOn(Dispatchers.IO)
