package io.gitlab.wylieyyyy.piptube

import javafx.embed.swing.JFXPanel
import javafx.scene.input.ScrollEvent
import javafx.stage.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import java.awt.Point
import javax.swing.JFrame
import javax.swing.JWindow

class WindowBoundsHandler(
    private val controlFrame: JFrame,
    private val videoWindow: JWindow,
    private val baseHeight: Int,
) {
    companion object {
        private const val BOUNDS_SETTLE_DELAY_MILLISECONDS = 1L
    }

    private val scrollMutex = Mutex()
    private var videoMoveOffset = Point()

    public fun prepareMove(startPoint: Point) {
        videoMoveOffset = videoWindow.location - startPoint
    }

    public suspend fun updateMove(currentPoint: Point) {
        videoWindow.location = currentPoint + videoMoveOffset
        controlFrame.location = videoWindow.location - Point(0, controlFrame.height)
    }

    public fun moveToBottomRight() {
        val oldControlRelativeX = controlFrame.location.x - videoWindow.location.x
        val controlVerticalInset = controlFrame.insets.top + controlFrame.insets.bottom

        val oldVideoBounds = videoWindow.bounds
        val screenBounds = Screen.getPrimary().visualBounds

        val newVideoX = (screenBounds.minX + screenBounds.width).toInt() - oldVideoBounds.width
        val newVideoY = (screenBounds.minY + screenBounds.height).toInt() - oldVideoBounds.height

        controlFrame.setLocation(newVideoX + oldControlRelativeX, newVideoY - controlVerticalInset - baseHeight)
        videoWindow.setLocation(newVideoX, newVideoY)
    }

    public suspend fun handleScroll(event: ScrollEvent) {
        if (!scrollMutex.tryLock()) return
        try {
            val oldControlBounds = controlFrame.bounds
            val controlVerticalInset = controlFrame.insets.top + controlFrame.insets.bottom

            val newControlHeight =
                (oldControlBounds.height + event.deltaY).toInt()
                    .coerceIn(controlVerticalInset, controlVerticalInset + baseHeight)

            val controlDeltaHeight = newControlHeight - oldControlBounds.height

            controlFrame.setBounds(
                oldControlBounds.x,
                oldControlBounds.y - controlDeltaHeight,
                oldControlBounds.width,
                newControlHeight,
            )

            delay(BOUNDS_SETTLE_DELAY_MILLISECONDS)
        } finally {
            scrollMutex.unlock()
        }
    }

    public fun resizeToBase() {
        val oldControlBounds = controlFrame.bounds
        val controlVerticalInset = controlFrame.insets.top + controlFrame.insets.bottom

        val controlDeltaY = oldControlBounds.height - controlVerticalInset

        controlFrame.setBounds(
            oldControlBounds.x,
            oldControlBounds.y + controlDeltaY,
            oldControlBounds.width,
            controlVerticalInset,
        )
    }

    public fun focusControlPane(shouldFocus: Boolean) {
        controlFrame.focusableWindowState = shouldFocus

        if (shouldFocus) {
            controlFrame.toFront()
            (controlFrame.contentPane.components.single() as JFXPanel).requestFocusInWindow()
        } else {
            controlFrame.setVisible(false)
            controlFrame.setVisible(true)
        }
    }
}

operator fun Point.plus(other: Point) = Point(x + other.x, y + other.y)

operator fun Point.minus(other: Point) = Point(x - other.x, y - other.y)
