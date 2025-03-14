package io.gitlab.wylieyyyy.piptube.videolist

import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.layout.HBox
import javafx.scene.paint.ImagePattern
import javafx.scene.shape.Circle
import org.schabi.newpipe.extractor.comments.CommentsInfoItem

class CommentCard(private val commentInfo: CommentsInfoItem) : HBox() {
    companion object {
        public const val SPACING = 10

        public const val AVATAR_RADIUS = 45
    }

    @FXML private lateinit var avatarCircle: Circle

    @FXML private lateinit var nameLabel: Label

    @FXML private lateinit var textLabel: Label

    init {
        val loader = FXMLLoader(this::class.java.getResource("comment_card.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<Parent>()
    }

    @Suppress("UnusedPrivateMember")
    @FXML
    private fun initialize() {
        commentInfo.uploaderAvatars.firstOrNull()?.also {
            // TODO: IllegalArgumentException
            avatarCircle.fill = ImagePattern(Image(it.url))
        }
        nameLabel.text = commentInfo.uploaderName
        textLabel.text = commentInfo.commentText.content
    }
}
