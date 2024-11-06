package io.gitlab.wylieyyyy.piptube.videolist

import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class InfoCard(
    private val labelText: String? = null,
    private val scope: CoroutineScope = MainScope(),
    private val navigate: (suspend (InfoCard) -> Unit)? = null,
) : HBox() {
    public var text
        get() = label.text
        set(value) = label.setText(value)

    @FXML private lateinit var label: Label

    init {
        val loader = FXMLLoader(this::class.java.getResource("info_card.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        labelText?.let { label.text = it }
        if (navigate != null) {
            onMouseClicked =
                handler {
                    if (it.button == MouseButton.PRIMARY) navigate.invoke(this)
                }
        }
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}
