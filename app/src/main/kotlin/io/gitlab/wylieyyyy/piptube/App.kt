package io.gitlab.wylieyyyy.piptube

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javax.swing.JFrame
import javax.swing.JWindow
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    SwingUtilities.invokeLater {
        val controlFrame =
            JFrame("PiPTube").apply {
                add(JFXPanel())
                setAlwaysOnTop(true)
                setResizable(false)
                setSize(FXMLController.BASE_WIDTH, insets.top + insets.bottom)
                defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                focusableWindowState = false
            }
        val videoWindow =
            JWindow().apply {
                add(JFXPanel())
                setAlwaysOnTop(true)
                setSize(FXMLController.BASE_WIDTH, FXMLController.BASE_HEIGHT)
                focusableWindowState = false
            }

        Platform.runLater {
            FXMLController(controlFrame, videoWindow)
        }
    }
}
