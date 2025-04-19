package io.gitlab.wylieyyyy.piptube.player

import io.gitlab.wylieyyyy.piptube.FXMLController
import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class StatusBar : VBox() {
    /** Predefined dimensional constants. */
    companion object {
        /** Uses common window width as seekbar's width. */
        public const val SEEKBAR_WIDTH = FXMLController.BASE_WIDTH

        /** Height of a collapsed seekbar. */
        public const val SEEKBAR_HEIGHT = 3

        /** Height of an expanded seekbar. */
        public const val SEEKBAR_EXPANDED_HEIGHT = SEEKBAR_HEIGHT * 3
    }

    @FXML private lateinit var progressBackgroundRectangle: Rectangle

    @FXML private lateinit var progressRectangle: Rectangle

    @FXML private lateinit var titleLabel: Label

    @FXML private lateinit var progressLabel: Label

    public lateinit var scope: CoroutineScope

    public lateinit var embeddedMediaPlayer: EmbeddedMediaPlayer

    /** Title displayed in the status bar. */
    public var title: String
        get() = titleLabel.text
        set(value) = titleLabel.setText(value)

    init {
        val loader = FXMLLoader(this::class.java.getResource("status_bar.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        progressBackgroundRectangle.onMouseClicked = handler(::handleSeekbarClicked)
        progressRectangle.onMouseClicked = handler(::handleSeekbarClicked)

        titleLabel.managedProperty().bind(titleLabel.visibleProperty())
        progressLabel.managedProperty().bind(progressLabel.visibleProperty())
    }

    /**
     * Expand or collapse the status bar.
     * Title and progress texts are visible and the progress bar is thickened when expanded.
     *
     * @param[value] True for expand, false for collapse.
     */
    public fun setExpanded(value: Boolean) {
        titleLabel.setVisible(value)
        progressLabel.setVisible(value)
        val height = if (value) SEEKBAR_EXPANDED_HEIGHT else SEEKBAR_HEIGHT
        progressBackgroundRectangle.height = height.toDouble()
        progressRectangle.height = height.toDouble()
    }

    /** Updates the progress text shown in the status bar using status from the player. */
    public fun updateVideoProgress() {
        fun Long.toDurationString(): String = toDuration(
            DurationUnit.MILLISECONDS,
        ).toComponents { hours, minutes, seconds, _ ->
            val minuteSecondPart =
                "${minutes.toString().padStart(2, '0')}:" +
                    "${seconds.toString().padStart(2, '0')}"
            if (hours != 0L) {
                "${hours.toString().padStart(2, '0')}:$minuteSecondPart"
            } else {
                minuteSecondPart
            }
        }

        progressRectangle.width = embeddedMediaPlayer.status().time().toDouble() /
            embeddedMediaPlayer.status().length() * SEEKBAR_WIDTH
        progressLabel.text =
            embeddedMediaPlayer.status().run {
                time().toDurationString() + '/' + length().toDurationString()
            }
    }

    private fun handleSeekbarClicked(event: MouseEvent) {
        embeddedMediaPlayer.controls().setPosition((event.x / SEEKBAR_WIDTH).toFloat())
        updateVideoProgress()
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> = object : EventHandler<T> {
        override fun handle(event: T) {
            scope.launch { block(event) }
        }
    }
}

/** Operator for multiplying a duration by a scalar. */
operator fun Duration.times(other: Double): Duration = multiply(other)

/** Operator for dividing a duration by a scalar. */
operator fun Duration.div(other: Double): Duration = divide(other)
