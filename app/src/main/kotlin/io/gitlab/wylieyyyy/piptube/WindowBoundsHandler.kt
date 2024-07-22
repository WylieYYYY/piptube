package io.gitlab.wylieyyyy.piptube

import javafx.scene.input.ScrollEvent
import javafx.stage.Screen
import javax.swing.JFrame

class WindowBoundsHandler(private val frame: JFrame, private val baseHeight: Int) {
    public fun moveToBottomRight() {
        val oldBounds = frame.bounds
        val screenBounds = Screen.getPrimary().visualBounds

        screenBounds.apply {
            frame.setLocation((minX + width).toInt() - oldBounds.width, (minY + height).toInt() - oldBounds.height)
        }
    }

    public fun handleScroll(event: ScrollEvent) {
        val oldBounds = frame.bounds
        val verticalInset = frame.insets.top + frame.insets.bottom

        val height =
            (oldBounds.height + event.deltaY).toInt()
                .coerceIn(baseHeight + verticalInset, baseHeight * 2)
        val deltaHeight = height - oldBounds.height

        frame.setBounds(oldBounds.x, oldBounds.y - deltaHeight, oldBounds.width, height)
    }

    public fun resizeToBase() {
        val oldBounds = frame.bounds
        val verticalInset = frame.insets.top + frame.insets.bottom

        val deltaY = oldBounds.height - (baseHeight + verticalInset)
        frame.setBounds(oldBounds.x, oldBounds.y + deltaY, oldBounds.width, baseHeight + verticalInset)
    }
}
