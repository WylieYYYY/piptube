package io.gitlab.wylieyyyy.piptube

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    SwingUtilities.invokeLater {
        val fxPanel = JFXPanel()
        val frame =
            JFrame("PiPTube").apply {
                add(fxPanel)
                setAlwaysOnTop(true)
                setResizable(false)
                setSize(FXMLController.BASE_WIDTH, FXMLController.BASE_HEIGHT * 2)
                defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                focusableWindowState = false
            }
        Platform.runLater {
            val parent = FXMLController(frame).parent
            fxPanel.scene = Scene(parent)
            frame.setVisible(true)
        }
    }
}