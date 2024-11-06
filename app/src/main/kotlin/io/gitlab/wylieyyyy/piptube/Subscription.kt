package io.gitlab.wylieyyyy.piptube

import com.mayakapps.kache.FileKache
import com.mayakapps.kache.KacheStrategy
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object StreamInfoItemSerializer : KSerializer<StreamInfoItem> {
    override val descriptor: SerialDescriptor = ByteArraySerializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: StreamInfoItem,
    ) {
        val stream = ByteArrayOutputStream()
        // TODO: IOException
        ObjectOutputStream(stream).use {
            // TODO: IOException
            it.writeObject(value)
        }
        encoder.encodeSerializableValue(ByteArraySerializer(), stream.toByteArray())
    }

    override fun deserialize(decoder: Decoder): StreamInfoItem {
        val buffer = decoder.decodeSerializableValue(ByteArraySerializer())
        // TODO: IOException
        return ObjectInputStream(ByteArrayInputStream(buffer)).use {
            // TODO: ClassNotFoundException, IOException, OptionalDataException
            it.readObject() as StreamInfoItem
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SubscriptionCache private constructor(
    public val seenItems: MutableList<
        @Serializable(StreamInfoItemSerializer::class)
        StreamInfoItem,
    >,
    public var lastUpdated: Long,
) {
    companion object {
        public suspend fun fromCacheOrNew(): SubscriptionCache {
            var newSubscription: SubscriptionCache? = null
            val cacheFilePath =
                cache.await().getOrPut("subscription") {
                    newSubscription = SubscriptionCache()
                    // TODO: IOException, FileNotFoundException
                    FileOutputStream(it).use {
                        it.write(Cbor.encodeToByteArray(serializer(), newSubscription))
                    }
                    true
                }

            newSubscription?.let { return@fromCacheOrNew it }
            // TODO: IOException, FileNotFoundException
            return FileInputStream(cacheFilePath).use {
                Cbor.decodeFromByteArray(serializer(), it.readAllBytes())
            }
        }

        private val REFRESH_COOLDOWN_SECONDS = 30.toDuration(DurationUnit.MINUTES).inWholeSeconds

        private val cache =
            MainScope().async {
                val path = Path(System.getProperty("user.home")).resolve(".cache").resolve("piptube")
                // TODO: fail mkdirs
                path.toFile().mkdirs()
                FileKache(directory = path.toString(), maxSize = 100L * 1024 * 1024) {
                    strategy = KacheStrategy.LRU
                }
            }
    }

    @Transient private val reverseTimeComparator =
        Comparator { left: StreamInfoItem, right: StreamInfoItem ->
            val leftSeconds = left.uploadDate?.offsetDateTime()?.toEpochSecond()
            val rightSeconds = right.uploadDate?.offsetDateTime()?.toEpochSecond()

            if (leftSeconds == null) {
                -1
            } else if (rightSeconds == null) {
                1
            } else {
                rightSeconds.compareTo(leftSeconds)
            }
        }

    public constructor() : this(mutableListOf(), 0)

    public suspend fun fetchUnseenItems(channels: List<ChannelInfoItem>) {
        val currentTime = System.currentTimeMillis() / 1.toDuration(DurationUnit.SECONDS).inWholeMilliseconds
        if (lastUpdated + REFRESH_COOLDOWN_SECONDS > currentTime) return

        val items = mutableListOf<StreamInfoItem>()

        for (channel in channels) {
            val extractor = NewPipe.getService(channel.serviceId).getFeedExtractor(channel.url)
            val unseenStreams =
                VideoListGenerator(extractor = extractor).unseenItems()
                    .filterIsInstance<StreamInfoItem>()

            items.addAll(
                unseenStreams.filter {
                    it.uploadDate?.offsetDateTime()?.toEpochSecond()?.let {
                        it > lastUpdated
                    } ?: false
                },
            )
        }

        items.sortWith(reverseTimeComparator)
        seenItems.addAll(0, items)
        lastUpdated = currentTime

        cache.await().put("subscription") {
            // TODO: IOException, FileNotFoundException
            FileOutputStream(it).use {
                it.write(Cbor.encodeToByteArray(serializer(), this))
            }
            true
        }
    }
}
