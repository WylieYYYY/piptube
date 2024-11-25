package io.gitlab.wylieyyyy.piptube.videolist

import io.gitlab.wylieyyyy.piptube.FXMLController
import io.gitlab.wylieyyyy.piptube.Subscription
import io.gitlab.wylieyyyy.piptube.SubscriptionCache
import io.gitlab.wylieyyyy.piptube.TabIdentifier
import io.gitlab.wylieyyyy.piptube.VideoListGenerator
import javafx.scene.layout.VBox
import kotlinx.coroutines.flow.collect

class SubscriptionPage(
    private val controller: FXMLController,
    private val subscription: Subscription,
    private val subscriptionCache: SubscriptionCache,
) : VBox() {
    init {
        val updateCard =
            InfoCard("Update subscription...") {
                controller.controlPane.withClearedVideoList {
                    val channels = subscription.channels()
                    subscriptionCache.fetchUnseenItems(channels).collect()

                    Pair(
                        TabIdentifier.SUBSCRIPTION,
                        VideoListGenerator(
                            seenItems =
                                listOf(VideoListGenerator.VideoListItem.Node(it)) +
                                    subscriptionCache.seenItems().map(
                                        VideoListGenerator.VideoListItem::InfoItem,
                                    ),
                        ),
                    )
                }
            }
        children.add(updateCard)
    }
}
