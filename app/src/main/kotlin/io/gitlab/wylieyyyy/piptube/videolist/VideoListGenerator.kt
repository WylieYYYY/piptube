package io.gitlab.wylieyyyy.piptube.videolist

import io.gitlab.wylieyyyy.piptube.TabIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException

/**
 * Tab that consists of one or two generators.
 * If two generators are specified, then the user is able to navigate between the two using controls.
 *
 * @property[identifier] Identifier for the tab. It should be generic over the generators since it is shared.
 * @property[primaryGenerator] Generator that will be displayed to the user first without any intervention.
 * @property[secondaryGenerator] Generator that the user can switch to using controls.
 * @constructor Creates a tab with the given identifier that can contain the specified generators.
 */
data class GeneratorTab(
    public val identifier: TabIdentifier,
    public val primaryGenerator: VideoListGenerator,
    public val secondaryGenerator: VideoListGenerator? = null,
)

/**
 * Generator for video list items.
 * This allows prepended static items and buffers dynamic items from an extractor.
 * Items can be randomly accessed by item index rather than dealing with pages.
 *
 * @param[seenItems] List of static video list items, the default value is an empty list.
 * @param[extractor] Extractor to fetch dynamic items from, null if no dynamic items are required.
 *  The default value is null.
 * @constructor Creates a generator with the given static and dynamic items.
 */
class VideoListGenerator(
    seenItems: List<VideoListItem> = listOf(),
    private val extractor: ListExtractor<out InfoItem>? = null,
) {
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

    private val sentinelInitialPage = ListExtractor.InfoItemsPage(listOf(), null, listOf())
    private var currentPage: ListExtractor.InfoItemsPage<out InfoItem> = sentinelInitialPage

    /**
     * Checks whether this generator is proper.
     * Proper here means that either this generator contains only static items,
     * or some dynamic items have been fetched.
     *
     * @return True if this generator is proper by this definition, false otherwise.
     */
    public fun isProper(): Boolean = extractor == null || currentPage !== sentinelInitialPage

    /**
     * Checks whether this generator has fetched all dynamic items.
     *
     * @return True if the generator contains only static items or all dynamic items have been
     *  fetched, false otherwise.
     */
    public fun isExhausted(): Boolean = extractor == null ||
        (currentPage !== sentinelInitialPage && !currentPage.hasNextPage()) ||
        currentPage === ListExtractor.InfoItemsPage.emptyPage<InfoItem>()

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
            items.addAll(unseenItems())
        }
        val hasNext = index + PAGE_SIZE < seenItems.size || !isExhausted()
        return Pair(items, hasNext)
    }

    /**
     * Fetches the next batch of unseen items.
     *
     * @return List of [VideoListItem],
     *  the variant of the items is always [VideoListItem.InfoItem].
     */
    public suspend fun unseenItems(): List<VideoListItem> {
        if (extractor == null) return listOf()

        return withContext(Dispatchers.IO) {
            // TODO: ExtractionException, IOException
            extractor.fetchPage()

            // TODO: ExtractionException, IOException
            val items =
                runCatching {
                    currentPage = nextPage()
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

    private fun nextPage(): ListExtractor.InfoItemsPage<out InfoItem> = currentPage.let {
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
