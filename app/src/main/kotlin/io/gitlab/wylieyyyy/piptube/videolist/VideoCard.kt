package io.gitlab.wylieyyyy.piptube.videolist

import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.imageio.ImageIO
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class VideoCard(
    private val streamInfo: StreamInfoItem,
    private val scope: CoroutineScope,
    private val navigate: suspend () -> Unit,
) : StackPane() {
    companion object {
        public const val HEIGHT = 100

        public const val SPACING = HEIGHT / 10

        public const val THUMBNAIL_WIDTH = 160

        public const val THUMBNAIL_HEIGHT = 90
    }

    @FXML private lateinit var button: Button

    @FXML private lateinit var thumbnailView: ImageView

    @FXML private lateinit var durationLabel: Label

    @FXML private lateinit var titleLabel: Label

    @FXML private lateinit var artistLabel: Label

    init {
        val loader = FXMLLoader(this::class.java.getResource("video_card.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        stylesheets.add(this::class.java.getResource("video_card.css").toString())

        if (streamInfo.duration != -1L) {
            durationLabel.text =
                streamInfo.duration.toDuration(DurationUnit.SECONDS).toComponents { hours, minutes, seconds, _ ->
                    val minuteSecondPart =
                        "${minutes.toString().padStart(2, '0')}:" +
                            "${seconds.toString().padStart(2, '0')}"
                    if (hours != 0L) {
                        "${hours.toString().padStart(2, '0')}:$minuteSecondPart"
                    } else {
                        minuteSecondPart
                    }
                }
        }
        streamInfo.thumbnails.firstOrNull()?.also {
            scope.launch(Dispatchers.IO) {
                // TODO: IOException, URISyntaxException, MalformedURLException
                val bufferedImage = ImageIO.read(URI(it.url).toURL())
                val byteArrayOutputStream = ByteArrayOutputStream()
                // TODO: IOException
                if (!ImageIO.write(bufferedImage, "JPEG", byteArrayOutputStream)) return@launch
                // TODO: IllegalArgumentException
                val image = Image(ByteArrayInputStream(byteArrayOutputStream.toByteArray()))
                withContext(Dispatchers.Main) {
                    thumbnailView.image = image
                }
            }
        }
        titleLabel.text = streamInfo.name
        streamInfo.uploaderName?.let { artistLabel.text = it }
        button.onAction = handler { navigate() }
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> = object : EventHandler<T> {
        override fun handle(event: T) {
            scope.launch { block(event) }
        }
    }
}
