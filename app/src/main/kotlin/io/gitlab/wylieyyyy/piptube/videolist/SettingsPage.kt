package io.gitlab.wylieyyyy.piptube.videolist

import io.gitlab.wylieyyyy.piptube.NewPipeImportService
import io.gitlab.wylieyyyy.piptube.Subscription
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import kotlinx.coroutines.CoroutineScope
import java.io.FileInputStream

class SettingsPage(
    private val scope: CoroutineScope,
    private val subscription: Subscription,
) : VBox() {
    init {
        val importCard =
            InfoCard("Import NewPipe subscription", scope) callback@{
                val fileChooser =
                    FileChooser().apply {
                        title = "Open exported file"
                        extensionFilters.addAll(
                            FileChooser.ExtensionFilter("NewPipe subscription files", "*.json"),
                        )
                    }
                val file = fileChooser.showOpenDialog(scene.window) ?: return@callback
                // TODO: FileNotFoundException
                subscription.import(NewPipeImportService, FileInputStream(file))
            }
        children.add(importCard)
    }
}
