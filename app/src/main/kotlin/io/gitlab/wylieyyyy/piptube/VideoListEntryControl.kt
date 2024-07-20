package io.gitlab.wylieyyyy.piptube

import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class VideoListEntryControl(private val streamInfo: StreamInfoItem, private val navigate: () -> Unit) : StackPane() {
    companion object {
        public const val HEIGHT = 100

        public const val SPACING = HEIGHT / 10
    }

    @FXML private lateinit var button: Button

    @FXML private lateinit var titleLabel: Label

    @FXML private lateinit var artistLabel: Label

    private val scope = MainScope()

    init {
        val loader = FXMLLoader(this::class.java.getResource("video_list_entry_control.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        titleLabel.text = streamInfo.name
        artistLabel.text = streamInfo.uploaderName
        button.onAction = handler { _ -> navigate() }
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}
