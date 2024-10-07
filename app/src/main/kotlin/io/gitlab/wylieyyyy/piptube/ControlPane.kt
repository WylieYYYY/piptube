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
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class ControlPane(
    private val streamingService: StreamingService,
    private val controller: FXMLController,
    private val windowBoundsHandler: WindowBoundsHandler,
    private val scope: CoroutineScope,
) : VBox() {
    @FXML private lateinit var searchField: SearchField

    @FXML private lateinit var tabList: TabPane

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
                        addToVideoList(listOf())
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val items = newValue.userData as List<InfoItem>
                        addToVideoList(items)
                    }
                }
            },
        )

        videoList.children.addListener { _: Observable ->
            progress.setVisible(videoList.children.isEmpty())
        }
    }

    public fun clearVideoList() {
        tabList.setDisable(true)
        videoList.children.clear()
    }

    public suspend fun addToVideoList(
        identifier: TabIdentifier,
        items: List<InfoItem>,
    ) {
        val matchedTab = tabList.tabs.firstOrNull { it.id == identifier.toString() }
        val targetTab =
            matchedTab ?: Tab().apply {
                userData = items
                id = identifier.toString()
                text = identifier.toString()
                tabList.tabs.add(this)
            }

        targetTab.userData = items
        if (tabList.selectionModel.selectedItem == matchedTab) {
            clearVideoList()
            addToVideoList(items)
        } else {
            tabList.selectionModel.select(targetTab)
        }
    }

    private suspend fun addToVideoList(items: List<InfoItem>) {
        tabList.setDisable(false)

        if (items.isEmpty()) {
            videoList.children.add(InfoCard())
        }
        videoList.children.addAll(
            items.mapNotNull {
                when (it) {
                    is ChannelInfoItem ->
                        ChannelCard(it, scope) {
                            clearVideoList()
                            val channelInfo: MutableList<InfoItem> =
                                withContext(Dispatchers.IO) {
                                    streamingService.getFeedExtractor(it.url)?.run {
                                        // TODO: ExtractionException, IOException
                                        fetchPage()
                                        // TODO: IOException, ExtractionException
                                        initialPage.items.toMutableList()
                                    } ?: mutableListOf()
                                }
                            channelInfo.addFirst(it)
                            addToVideoList(TabIdentifier(TabIdentifier.TabType.CHANNEL, it.name), channelInfo)
                        }
                    is StreamInfoItem ->
                        VideoCard(it, scope) {
                            windowBoundsHandler.resizeToBase()
                            videoList.children.clear()
                            // TODO: ExtractionException
                            val relatedInfo = controller.gotoVideoUrl(it.url).relatedItems?.items ?: listOf()
                            addToVideoList(TabIdentifier.RELATED, relatedInfo)
                        }
                    else -> null
                }
            },
        )
    }
}
