package io.gitlab.wylieyyyy.piptube.videolist

import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.layout.HBox
import javafx.scene.paint.ImagePattern
import javafx.scene.shape.Circle
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Safelist
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.jsoup.nodes.Node as JsoupNode

class CommentCard(private val commentInfo: CommentsInfoItem) : HBox() {
    companion object {
        public const val SPACING = 10

        public const val AVATAR_RADIUS = 45
    }

    @FXML private lateinit var avatarCircle: Circle

    @FXML private lateinit var nameLabel: Label

    @FXML private lateinit var textFlow: TextFlow

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
        parseComment(textFlow, commentInfo.commentText)
    }

    private fun parseComment(flow: TextFlow, comment: Description) {
        if (comment.type != Description.HTML) {
            flow.children.add(Text(comment.content))
            return
        }

        val safelist = Safelist()
        safelist.addTags("a", "b", "br")
        var node = Jsoup.parseBodyFragment(Jsoup.clean(comment.content, safelist)).body().firstChild()

        while (node != null) {
            parseJsoupNode(node)?.let {
                flow.children.add(it)
            }
            node = node.nextSibling()
        }
    }

    private fun parseJsoupNode(node: JsoupNode): Node? {
        if (node !is Element) {
            return if (node is TextNode) Text(node.text()) else null
        }

        return when (node.nodeName()) {
            "a" -> Hyperlink(node.text())
            "b" -> Text(node.text()).apply {
                style = "-fx-font-weight: bold"
            }
            "br" -> Text("\n")
            else -> error("Unexpected node type")
        }
    }
}
