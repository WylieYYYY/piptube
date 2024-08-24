package io.gitlab.wylieyyyy.piptube

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javax.swing.JFrame
import javax.swing.JWindow
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        val controlFrame =
            JFrame("PiPTube").apply {
                add(JFXPanel())
                setAlwaysOnTop(true)
                setUndecorated(true)
                setResizable(false)
                defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                focusableWindowState = false
            }
        val videoWindow =
            JWindow().apply {
                add(JFXPanel())
                setAlwaysOnTop(true)
                focusableWindowState = false
            }

        Platform.runLater {
            FXMLController(controlFrame, videoWindow)
        }
    }
}
