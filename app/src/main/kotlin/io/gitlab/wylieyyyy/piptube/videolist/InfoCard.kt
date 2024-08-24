package io.gitlab.wylieyyyy.piptube.videolist

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.layout.HBox

class InfoCard : HBox() {
    init {
        val loader = FXMLLoader(this::class.java.getResource("info_card.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }
}
