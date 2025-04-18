package io.gitlab.wylieyyyy.piptube

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javax.swing.JFrame
import javax.swing.JWindow
import javax.swing.SwingUtilities

/**
 * Main entry point for the application.
 * Windows consist of Awt compatible Swing windows containing JavaFx panels.
 * This is because the focusable state of windows are only available in Awt.
 */
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
            JWindow(controlFrame).apply {
                add(JFXPanel())
                setAlwaysOnTop(true)
                focusableWindowState = false
            }

        Platform.runLater {
            FXMLController(controlFrame, videoWindow)
        }
    }
}
