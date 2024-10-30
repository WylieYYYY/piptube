package io.gitlab.wylieyyyy.piptube

import javafx.embed.swing.JFXPanel
import javafx.geometry.HorizontalDirection
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

    public fun horizontalDirection(): HorizontalDirection {
        val screenBounds = Screen.getPrimary().visualBounds
        val minVideoWindowX = screenBounds.minX + screenBounds.width / 2 - videoWindow.width / 2
        return if (videoWindow.x > minVideoWindowX) HorizontalDirection.RIGHT else HorizontalDirection.LEFT
    }

    public fun moveToBottom(direction: HorizontalDirection) {
        val oldControlRelative = controlFrame.location - videoWindow.location

        val screenBounds = Screen.getPrimary().visualBounds

        val newVideoX =
            if (direction == HorizontalDirection.RIGHT) {
                (screenBounds.minX + screenBounds.width).toInt() - videoWindow.width
            } else {
                screenBounds.minX.toInt()
            }

        val newVideoY = (screenBounds.minY + screenBounds.height).toInt() - videoWindow.height

        controlFrame.setLocation(newVideoX + oldControlRelative.x, newVideoY + oldControlRelative.y)
        videoWindow.setLocation(newVideoX, newVideoY)
    }

    public suspend fun handleScroll(event: ScrollEvent): Boolean {
        if (!scrollMutex.tryLock()) return true
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
            return controlDeltaHeight != 0
        } finally {
            scrollMutex.unlock()
        }
    }

    public fun resizeToBase(): Boolean {
        val oldControlBounds = controlFrame.bounds
        val controlVerticalInset = controlFrame.insets.top + controlFrame.insets.bottom

        val controlDeltaY = oldControlBounds.height - controlVerticalInset

        controlFrame.setBounds(
            oldControlBounds.x,
            oldControlBounds.y + controlDeltaY,
            oldControlBounds.width,
            controlVerticalInset,
        )

        return controlDeltaY != 0
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
