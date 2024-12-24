package io.gitlab.wylieyyyy.piptube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit
import okhttp3.Request as OkRequest

object DownloaderImpl : Downloader() {
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
    private const val HTTP_STATUS_TOO_MANY_REQUESTS = 429

    private val client = OkHttpClient.Builder().readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    override fun execute(request: Request): Response {
        val requestBody = request.dataToSend()?.toRequestBody()
        val requestBuilder =
            OkRequest.Builder().method(request.httpMethod(), requestBody)
                .url(request.url()).addHeader("User-Agent", USER_AGENT)

        for ((headerName, headerValues) in request.headers()) {
            if (headerValues.size > 1) {
                requestBuilder.removeHeader(headerName)
                for (headerValue in headerValues) {
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValues.size == 1) {
                requestBuilder.header(headerName, headerValues[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (response.code == HTTP_STATUS_TOO_MANY_REQUESTS) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", request.url())
        }

        val responseBody = response.body?.string()
        val latestUrl = response.request.url.toString()
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            latestUrl,
        )
    }
}
