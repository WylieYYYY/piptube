package io.gitlab.wylieyyyy.piptube

import javafx.beans.Observable
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.ProgressIndicator
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class ControlPane(
    private val controller: FXMLController,
    private val windowBoundsHandler: WindowBoundsHandler,
    private val scope: CoroutineScope,
) : VBox() {
    @FXML private lateinit var searchField: SearchField

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
        searchField.controller = controller
        searchField.windowBoundsHandler = windowBoundsHandler
        searchField.scope = scope

        videoList.children.addListener { _: Observable ->
            progress.setVisible(videoList.children.isEmpty())
        }
    }

    public fun clearVideoList() {
        videoList.children.clear()
    }

    public suspend fun addToVideoList(items: List<InfoItem>) {
        videoList.children.addAll(
            items.filterIsInstance<StreamInfoItem>().map {
                VideoListEntryControl(it) {
                    scope.launch {
                        windowBoundsHandler.resizeToBase()
                        videoList.children.clear()
                        // TODO: ExtractionException
                        val relatedInfo = controller.gotoVideoUrl(it.url).relatedItems?.items ?: listOf()
                        addToVideoList(relatedInfo)
                    }
                }
            },
        )
    }
}
