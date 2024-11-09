package io.gitlab.wylieyyyy.piptube.videolist

import io.gitlab.wylieyyyy.piptube.ChannelIdentifier
import io.gitlab.wylieyyyy.piptube.Subscription
import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.layout.StackPane
import javafx.scene.paint.ImagePattern
import javafx.scene.shape.Circle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.imageio.ImageIO

class ChannelCard(
    private val channelInfo: ChannelInfoItem,
    private val scope: CoroutineScope,
    private val subscription: Subscription,
    private val navigate: suspend () -> Unit,
) : StackPane() {
    companion object {
        public const val HEIGHT = 100

        public const val SPACING = HEIGHT / 10

        public const val AVATAR_RADIUS = 45
    }

    @FXML private lateinit var button: Button

    @FXML private lateinit var avatarCircle: Circle

    @FXML private lateinit var nameLabel: Label

    @FXML private lateinit var descriptionLabel: Label

    @FXML private lateinit var subscribeButton: Button

    init {
        val loader = FXMLLoader(this::class.java.getResource("channel_card.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        channelInfo.thumbnails.firstOrNull()?.also {
            scope.launch(Dispatchers.IO) {
                // TODO: IOException, URISyntaxException, MalformedURLException
                val bufferedImage = ImageIO.read(URI(it.url).toURL())
                val byteArrayOutputStream = ByteArrayOutputStream()
                // TODO: IOException
                if (!ImageIO.write(bufferedImage, "JPEG", byteArrayOutputStream)) return@launch
                // TODO: IllegalArgumentException
                val pattern = ImagePattern(Image(ByteArrayInputStream(byteArrayOutputStream.toByteArray())))
                withContext(Dispatchers.Main) {
                    avatarCircle.fill = pattern
                }
            }
        }
        nameLabel.text = channelInfo.name
        descriptionLabel.text = channelInfo.description
        subscribeButton.text = if (subscription.getIsSubscribed(ChannelIdentifier(channelInfo))) "-" else "+"
        subscribeButton.onAction =
            handler {
                subscribeButton.text = if (subscription.toggle(ChannelIdentifier(channelInfo))) "-" else "+"
            }
        button.onAction = handler { navigate() }
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}
