package io.gitlab.wylieyyyy.piptube

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream

interface ImportService {
    suspend fun importSubscription(stream: InputStream): List<ChannelIdentifier>
}

private val jsonFormat = Json { ignoreUnknownKeys = true }

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
