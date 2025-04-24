package io.gitlab.wylieyyyy.piptube.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Provider for parsing streams according to their import source. */
interface ImportService {
    /**
     * Imports subscriptions from the given stream.
     *
     * @param[stream] Stream to read and parse subscriptions from.
     * @return List of channel identifiers of channels that are subscribed to.
     */
    suspend fun importSubscription(stream: InputStream): List<ChannelIdentifier>
}

private val jsonFormat = Json { ignoreUnknownKeys = true }

/** Import service for NewPipe exported Json. */
object NewPipeImportService : ImportService {
    @Serializable
    private data class NewPipeExport(public val subscriptions: List<ChannelIdentifier>)

    override suspend fun importSubscription(stream: InputStream): List<ChannelIdentifier> {
        val byteArrayOutputStream = ByteArrayOutputStream()
        // TODO: IOException
        stream.copyTo(byteArrayOutputStream)
        val buffer = byteArrayOutputStream.toString("UTF-8")
        // TODO: SerializationException
        return jsonFormat.decodeFromString<NewPipeExport>(buffer).subscriptions
    }
}
