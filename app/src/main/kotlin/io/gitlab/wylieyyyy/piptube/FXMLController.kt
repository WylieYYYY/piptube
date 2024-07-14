package io.gitlab.wylieyyyy.piptube

import javafx.event.ActionEvent
import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import javax.swing.JFrame

class FXMLController {
    companion object {
        public const val BASE_WIDTH = 640

        public const val BASE_HEIGHT = 360
    }

    @FXML private lateinit var videoArea: StackPane

    @FXML private lateinit var videoView: MediaView

    @FXML private lateinit var playButton: Button

    private val scope = MainScope()
    private val youtubeService =
        run {
            NewPipe.init(DownloaderImpl)
            NewPipe.getService("YouTube")
        }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        videoArea.onScroll = handler(::onVideoAreaScrolled)
        val actionHandler: suspend (ActionEvent) -> Unit = { _ -> onPlayButtonActioned() }
        playButton.onAction = handler(actionHandler)
    }

    private suspend fun onVideoAreaScrolled(event: ScrollEvent) {
        val frame = videoView.scene.userData as JFrame

        val oldBounds = frame.bounds
        val verticalInset = frame.insets.top + frame.insets.bottom

        val height =
            (oldBounds.height + event.deltaY).toInt()
                .coerceIn(BASE_HEIGHT + verticalInset, BASE_HEIGHT * 2)
        val deltaHeight = height - oldBounds.height

        frame.setBounds(oldBounds.x, oldBounds.y - deltaHeight, oldBounds.width, height)
    }

    private suspend fun onPlayButtonActioned() {
        playButton.setVisible(false)

        val extractor =
            withContext(Dispatchers.IO) {
                val streamLinkHandler = youtubeService.streamLHFactory.fromId("EdHGrnuCEo4")
                val extractor = youtubeService.getStreamExtractor(streamLinkHandler)

                extractor.fetchPage()
                extractor
            }
        val media = Media(extractor.getVideoStreams()[0].content)
        val player = MediaPlayer(media)

        videoView.mediaPlayer = player
        player.play()
    }

    private fun <T : Event> handler(block: suspend (event: T) -> Unit): EventHandler<T> =
        object : EventHandler<T> {
            override fun handle(event: T) {
                scope.launch { block(event) }
            }
        }
}
