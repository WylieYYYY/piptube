package io.gitlab.wylieyyyy.piptube

import com.mayakapps.kache.FileKache
import com.mayakapps.kache.KacheStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.SetSerializer
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
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import java.io.Serializable as JavaSerializable

/**
 * [KSerializer] implementation for arbitrary Java serializable class.
 *
 * @param[clazz] Target Java class which the deserialized data cast to.
 * @constructor Creates a serializer for the given class, generic parameter should be the same class.
 */
open class JavaSerializableSerializer<T : JavaSerializable>(private val clazz: Class<T>) : KSerializer<T> {
    override val descriptor: SerialDescriptor = ByteArraySerializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) {
        val stream = ByteArrayOutputStream()
        // TODO: IOException
        ObjectOutputStream(stream).use {
            // TODO: IOException
            it.writeObject(value)
        }
        encoder.encodeSerializableValue(ByteArraySerializer(), stream.toByteArray())
    }

    override fun deserialize(decoder: Decoder): T {
        val buffer = decoder.decodeSerializableValue(ByteArraySerializer())
        // TODO: IOException
        return ObjectInputStream(ByteArrayInputStream(buffer)).use {
            // TODO: ClassNotFoundException, IOException, OptionalDataException
            val readObject = clazz.cast(it.readObject())
            // TODO: IllegalArgumentException
            requireNotNull(readObject)
            readObject
        }
    }
}

/** [KSerializer] for NewPipe [StreamInfoItem]. */
object StreamInfoItemSerializer : JavaSerializableSerializer<StreamInfoItem>(StreamInfoItem::class.java)

private val reverseTimeComparator =
    Comparator.comparing { item: StreamInfoItem ->
        item.uploadDate?.offsetDateTime()?.toEpochSecond() ?: -1
    }.reversed()

private val uniqueStreamComparator =
    Comparator.comparing(StreamInfoItem::getServiceId).thenComparing(StreamInfoItem::getUrl)

/**
 * [KSerializer] for a tree set of NewPipe [StreamInfoItem].
 * A wrapper of [SetSerializer] since it does not support tree set directly.
 */
object StreamInfoItemTreeSetSerializer : KSerializer<TreeSet<StreamInfoItem>> {
    override val descriptor: SerialDescriptor = SetSerializer(StreamInfoItemSerializer).descriptor

    override fun serialize(
        encoder: Encoder,
        value: TreeSet<StreamInfoItem>,
    ) {
        encoder.encodeSerializableValue(SetSerializer(StreamInfoItemSerializer), value)
    }

    override fun deserialize(decoder: Decoder): TreeSet<StreamInfoItem> = TreeSet(
        reverseTimeComparator.then(uniqueStreamComparator),
    ).apply {
        addAll(decoder.decodeSerializableValue(SetSerializer(StreamInfoItemSerializer)))
    }
}

/**
 * Lightweight alternative to [ChannelInfoItem] which only stores key data.
 *
 * @param[serviceId] Service ID which is static and assigned by NewPipe,
 *  deserializes to snake case to match NewPipe exported JSON casing.
 * @property[serviceId] Service ID which is static and assigned by NewPipe,
 *  deserializes to snake case to match NewPipe exported JSON casing.
 * @param[url] URL to the channel page.
 * @property[url] URL to the channel page.
 * @constructor Creates an identifier from key data.
 */
@Serializable
public data class ChannelIdentifier(
    @SerialName("service_id") public val serviceId: Int,
    public val url: String,
) {
    /**
     * Creates an identifier from an existing [ChannelInfoItem].
     *
     * @param[channel] [ChannelInfoItem] to extract key data from.
     */
    public constructor(channel: ChannelInfoItem) : this(channel.serviceId, channel.url)
}

/**
 * Container for subscription related data, which is currently a set of channel identifiers.
 * Cbor serializer requires an experimental opt in.
 *
 * All operations that change the subscription will be
 * saved to the persistent storage in the same call.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Subscription private constructor(private val channels: MutableSet<ChannelIdentifier>) {
    /**
     * Factory method for creating the subscription container.
     * There are no other constructors as this must accompany persistent storage set up.
     */
    companion object {
        /**
         * Fetches subscription container from persistent storage.
         * Creates and stores an empty container if there isn't one that was persisted.
         *
         * @return Subscription container, either new or from persistent storage.
         */
        public suspend fun fromStorageOrNew(): Subscription = withContext(Dispatchers.IO) {
            // TODO: fail mkdirs
            PATH.parent.toFile().mkdirs()

            runCatching {
                FileInputStream(PATH.toString()).use {
                    // TODO: IOException, SerializationException
                    Cbor.decodeFromByteArray<Subscription>(serializer(), it.readAllBytes())
                }
            }.recoverCatching {
                when (it) {
                    is FileNotFoundException -> {
                        val newSubscription = Subscription(mutableSetOf())
                        newSubscription.requestSave()
                        newSubscription
                    }
                    else -> throw it
                }
            }.getOrThrow()
        }

        private val PATH =
            Path(System.getProperty("user.home"))
                .resolve(".config").resolve("piptube").resolve("subscription.cbor")
    }

    @Transient private val setMutex = Mutex()

    @Transient private val hasPending = AtomicBoolean(false)

    /**
     * Imports subscriptions to the current instance.
     *
     * @param[service] Import service to parse the stream to obtain a list of channel identifiers.
     * @param[stream] Generic input stream to relay to the import service for parsing.
     */
    public suspend fun import(
        service: ImportService,
        stream: InputStream,
    ) {
        val importedChannels = withContext(Dispatchers.IO) { service.importSubscription(stream) }
        setMutex.withLock { channels.addAll(importedChannels) }
        requestSave()
    }

    /**
     * Gets the channels that are stored in this subscription container.
     * To check for membership, [getIsSubscribed] should be used instead to prevent excessive locking.
     *
     * @return List of channel identifiers of subscribed channels.
     */
    public suspend fun channels(): List<ChannelIdentifier> = setMutex.withLock { channels.toList() }

    /**
     * Checks whether a channel is subscribed to.
     *
     * @param[channel] Identifier for the channel to be queried about.
     * @return True if a channel is subscribed to, false otherwise.
     */
    public fun getIsSubscribed(channel: ChannelIdentifier) = channel in channels

    /**
     * Toggles the subscription status of a channel.
     *
     * @param[channel] Identifier for the channel to be toggled.
     * @return True if the channel is now subscribed to after toggling, false otherwise.
     */
    public suspend fun toggle(channel: ChannelIdentifier): Boolean {
        var isAddition: Boolean
        setMutex.withLock {
            isAddition = !channels.remove(channel)
            if (isAddition) channels.add(channel)
        }
        requestSave()
        return isAddition
    }

    private suspend fun requestSave() {
        if (hasPending.getAndSet(true)) return

        while (hasPending.getAndSet(false)) {
            withContext(Dispatchers.IO) {
                // TODO: IOException, FileNotFoundException
                FileOutputStream(PATH.toString()).use {
                    it.write(Cbor.encodeToByteArray(serializer(), this@Subscription))
                }
            }
        }
    }
}

private fun <T> serializer(): KSerializer<T> = throw NotImplementedError(
    "Serializer implementation should be provided by the plugin",
)

/**
 * Container for caching videos from subscribed channels.
 * Cbor serializer requires an experimental opt in.
 *
 * @property[lastUpdated] When the cache was updated,
 *  represented by the number of seconds since Unix epoch.
 *  This is sufficient as the time is only used for cooldown and does not need to be precise.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SubscriptionCache private constructor(
    @Serializable(StreamInfoItemTreeSetSerializer::class)
    private val seenItems: TreeSet<
        @Serializable(StreamInfoItemSerializer::class)
        StreamInfoItem,
        >,
    public var lastUpdated: Long,
) {
    /**
     * Factory method for creating the cache container.
     * There are no other constructors as this must accompany persistent storage set up.
     */
    companion object {
        /**
         * Fetches cache container from persistent storage.
         * Creates and stores an empty container if there isn't one that was persisted.
         *
         * @return Cache container, either new or from persistent storage.
         */
        public suspend fun fromCacheOrNew(): SubscriptionCache {
            return withContext(Dispatchers.IO) {
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

                newSubscription?.let { return@withContext it }
                // TODO: FileNotFoundException
                FileInputStream(cacheFilePath).use {
                    // TODO: IOException, SerializationException
                    Cbor.decodeFromByteArray(serializer(), it.readAllBytes())
                }
            }
        }

        private val REFRESH_COOLDOWN_SECONDS = 30.toDuration(DurationUnit.MINUTES).inWholeSeconds

        private val cache =
            MainScope().async(Dispatchers.IO) {
                val path = Path(System.getProperty("user.home")).resolve(".cache").resolve("piptube")
                // TODO: fail mkdirs
                path.toFile().mkdirs()
                FileKache(directory = path.toString(), maxSize = 100L * 1024 * 1024) {
                    strategy = KacheStrategy.LRU
                }
            }
    }

    @Transient private val setMutex = Mutex()

    private constructor() : this(TreeSet(reverseTimeComparator.then(uniqueStreamComparator)), 0)

    /**
     * Gets a list of [StreamInfoItem] that are cached.
     * There is no check for whether the streams are from channels that are currently subscribed to.
     *
     * @return List of [StreamInfoItem] that are cached.
     */
    public suspend fun seenItems(): List<StreamInfoItem> = setMutex.withLock {
        seenItems.toList()
    }

    /**
     * Fetches new items from the given channels and write to the cache,
     * if cooldown period has not elapsed, do nothing.
     * Do not cancel execution immediately after all identifiers have been received from the flow,
     * as write will occur after all channel identifiers are yielded from the flow.
     *
     * @param[channels] Channels to fetch new items from.
     * @param[ignoreCooldown] True if the cooldown should be ignored, false otherwise.
     *  The default value is false.
     * @return Flow of channel identifiers that have just been fetched.
     *  This will be of the same length of the given iterable once the fetches are finished.
     *  Or of zero length if the cooldown period has not elapsed.
     */
    public suspend fun fetchUnseenItems(
        channels: Iterable<ChannelIdentifier>,
        ignoreCooldown: Boolean = false,
    ): Flow<ChannelIdentifier> {
        val currentTime = System.currentTimeMillis() / 1.toDuration(DurationUnit.SECONDS).inWholeMilliseconds
        if (lastUpdated + REFRESH_COOLDOWN_SECONDS > currentTime && !ignoreCooldown) return flowOf()

        return flow {
            for (channel in channels) {
                val extractor = NewPipe.getService(channel.serviceId).getFeedExtractor(channel.url)
                val unseenStreams =
                    VideoListGenerator(extractor = extractor).unseenItems()
                        .filterIsInstance<VideoListGenerator.VideoListItem.InfoItem<StreamInfoItem>>()
                        .map { it.item }

                setMutex.withLock {
                    seenItems.addAll(unseenStreams)
                }

                emit(channel)
            }

            lastUpdated = currentTime

            cache.await().put("subscription") {
                // TODO: IOException, FileNotFoundException
                FileOutputStream(it).use {
                    it.write(Cbor.encodeToByteArray(serializer(), this@SubscriptionCache))
                }
                true
            }
        }.flowOn(Dispatchers.IO)
    }
}
