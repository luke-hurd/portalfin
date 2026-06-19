package org.jellyfin.mobile.downloads

import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import java.io.IOException
import kotlin.coroutines.resumeWithException

class FileDownloader(
    private val okHttpClient: OkHttpClient,
) {
    fun interface ProgressCallback {
        suspend fun onProgress(downloaded: Long, total: Long)

        companion object Empty : ProgressCallback {
            override suspend fun onProgress(downloaded: Long, total: Long) = Unit
        }
    }

    private suspend fun download(
        api: ApiClient,
        from: Uri,
        rangeStart: Long? = null,
    ): Response {
        val authorizationHeader = AuthorizationHeaderBuilder.buildHeader(
            clientName = api.clientInfo.name,
            clientVersion = api.clientInfo.version,
            deviceId = api.deviceInfo.id,
            deviceName = api.deviceInfo.name,
            accessToken = api.accessToken,
        )

        val request = Request.Builder().apply {
            url(from.toString())

            header("Authorization", authorizationHeader)
            rangeStart?.let { header("Range", "bytes=$rangeStart-") }
        }.build()

        val response = okHttpClient.newCall(request).await()

        // 416 (Requested Range Not Satisfiable) can happen when we've already fully downloaded the file
        if (response.code == 416 && rangeStart != null && rangeStart >= response.getContentRange().total) return response

        // Throw for other unsuccessful responses
        if (!response.isSuccessful) throw IOException("Unexpected response $response")

        return response
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { cause, response, _ ->
                        response.close()
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            },
        )

        continuation.invokeOnCancellation {
            cancel()
        }
    }

    private fun Response.getContentRange() = when (code) {
        // Transcoded streams are sent chunked with no Content-Length. Treat a
        // missing length as "unknown total": start at 0, total 0 (the progress
        // bar just shows indeterminate). Static files still report a length.
        200 -> header("Content-Length")
            ?.let(ContentRange::fromContentLengthHeader)
            ?: ContentRange(start = 0, end = 0, total = 0)
        206, 416 -> requireNotNull(header("Content-Range")).let(ContentRange::fromContentRangeHeader)
        else -> error("Invalid response code $code")
    }

    private suspend fun save(
        response: Response,
        to: ParcelFileDescriptor,
        progressCallback: ProgressCallback,
    ) = withContext(Dispatchers.IO) {
        val contentRange = response.getContentRange()

        val output = ParcelFileDescriptor.AutoCloseOutputStream(to)
        output.channel.position(contentRange.start)

        val inputStream = response.body?.byteStream() ?: error("Response does not contain a body")
        inputStream.use { inputStream ->
            output.use { outputFile ->
                val buffer = ByteArray(10240)
                var totalRead = contentRange.start
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    coroutineContext.ensureActive()

                    outputFile.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    progressCallback.onProgress(totalRead, contentRange.total)
                }
            }
        }
    }

    suspend fun downloadAndSave(
        api: ApiClient,
        from: Uri,
        to: ParcelFileDescriptor,
        progressCallback: ProgressCallback = ProgressCallback.Empty,
        // Static-file downloads can resume from a byte offset. Transcoded
        // streams cannot (the server generates them on the fly), so we always
        // fetch from the start for those.
        resumable: Boolean = true,
    ) {
        val rangeStart = if (resumable) to.statSize else null
        val response = download(api, from, rangeStart)
        save(response, to, progressCallback)
    }
}
