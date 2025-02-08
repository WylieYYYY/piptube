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

/**
 * Handler for window geometry changes.
 *
 * @param[controlFrame] Top control pane window.
 * @param[videoWindow] Bottom video player window.
 * @param[baseHeight] Base maximum height for the control pane window,
 *  other calculations are relative to the window geometry at the time.
 * @constructor Creates a handler that manipulates the given windows.
 */
class WindowBoundsHandler(
    private val controlFrame: JFrame,
    private val videoWindow: JWindow,
    private val baseHeight: Int,
) {
    private companion object {
        private const val BOUNDS_SETTLE_DELAY_MILLISECONDS = 1L
    }

    private val scrollMutex = Mutex()
    private var videoMoveOffset = Point()

    /**
     * Function to call before [updateMove] is called,
     * to calculate the window location relative to the event.
     *
     * @param[startPoint] Where the move is starting from.
     */
    public fun prepareMove(startPoint: Point) {
        videoMoveOffset = videoWindow.location - startPoint
    }

    /**
     * Updates the window locations when the move event has an updated location.
     *
     * @param[currentPoint] Current location of the move event.
     */
    public suspend fun updateMove(currentPoint: Point) {
        videoWindow.location = currentPoint + videoMoveOffset
        controlFrame.location = videoWindow.location - Point(0, controlFrame.height)
    }

    /**
     * Calculates whether the video window is on the left or right half of the screen.
     *
     * @return Left or right for the location on screen.
     */
    public fun horizontalDirection(): HorizontalDirection {
        val screenBounds = Screen.getPrimary().visualBounds
        val minVideoWindowX = screenBounds.minX + screenBounds.width / 2 - videoWindow.width / 2
        return if (videoWindow.x > minVideoWindowX) HorizontalDirection.RIGHT else HorizontalDirection.LEFT
    }

    /**
     * Move the windows to a bottom corner of the screen.
     *
     * @param[direction] Left or right corner at the bottom.
     */
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

    /**
     * Handles a scroll event to resize the control pane window.
     * Also handles call concurrency so that this function can be called repeatedly.
     *
     * @param[event] JavaFx scroll event for the delta Y.
     * @return True if the control pane window has changed height,
     *  false if it has reached its size limits.
     */
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

    /**
     * Resize the control pane window to only display the video player window.
     *
     * @return True if the control pane window has changed height,
     *  false if it was already minimized.
     */
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

    /**
     * Focus or unfocus the control pane window.
     *
     * @param[shouldFocus] True to focus the control pane window, false to unfocus.
     */
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

/** Operator for adding points component-wise. */
operator fun Point.plus(other: Point) = Point(x + other.x, y + other.y)

/** Operator for minusing points component-wise. */
operator fun Point.minus(other: Point) = Point(x - other.x, y - other.y)
