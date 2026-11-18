package com.cleanphone.freespace.utility.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.BufferedSink
import java.io.InterruptedIOException
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

internal data class NetworkTransferSample(
    val bytes: Long,
    val elapsedNanos: Long
)

internal sealed interface NetworkTransferResult {
    data class Success(val sample: NetworkTransferSample) : NetworkTransferResult
    data class Failure(val reason: NetworkTransferFailure) : NetworkTransferResult
}

internal enum class NetworkTransferFailure {
    DNS,
    CONNECT,
    TLS,
    TIMEOUT,
    HTTP,
    INCOMPLETE,
    IO
}

internal data class NetworkSpeedEndpoints(
    val downloadBaseUrl: HttpUrl,
    val uploadUrl: HttpUrl
)

internal interface NetworkSpeedTransport {
    suspend fun download(bytes: Long, timeoutMs: Long): NetworkTransferResult
    suspend fun upload(bytes: Long, timeoutMs: Long): NetworkTransferResult
}

internal class OkHttpNetworkSpeedTransport(
    private val endpoints: NetworkSpeedEndpoints = DEFAULT_ENDPOINTS,
    private val baseClient: OkHttpClient = DEFAULT_CLIENT,
    private val nanoTime: () -> Long = System::nanoTime
) : NetworkSpeedTransport {

    override suspend fun download(bytes: Long, timeoutMs: Long): NetworkTransferResult {
        if (bytes <= 0 || timeoutMs <= 0) return failure(NetworkTransferFailure.IO)
        val url = endpoints.downloadBaseUrl.newBuilder()
            .setQueryParameter("bytes", bytes.toString())
            .build()
        val request = Request.Builder().url(url).get().build()
        return execute(request, timeoutMs) { response ->
            if (!response.isSuccessful) return@execute failure(NetworkTransferFailure.HTTP)
            val body = response.body ?: return@execute failure(NetworkTransferFailure.INCOMPLETE)
            val input = body.byteStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var transferredBytes = 0L
            val startNanos = nanoTime()
            while (transferredBytes <= bytes) {
                val remainingWithOverflowByte = bytes - transferredBytes + 1L
                val readSize = minOf(buffer.size.toLong(), remainingWithOverflowByte).toInt()
                val readCount = input.read(buffer, 0, readSize)
                if (readCount < 0) break
                transferredBytes += readCount
            }
            val elapsedNanos = nanoTime() - startNanos
            if (transferredBytes == bytes && elapsedNanos > 0) {
                NetworkTransferResult.Success(
                    NetworkTransferSample(transferredBytes, elapsedNanos)
                )
            } else {
                failure(NetworkTransferFailure.INCOMPLETE)
            }
        }
    }

    override suspend fun upload(bytes: Long, timeoutMs: Long): NetworkTransferResult {
        if (bytes <= 0 || bytes > Int.MAX_VALUE || timeoutMs <= 0) {
            return failure(NetworkTransferFailure.IO)
        }
        val requestBody = TimedRequestBody(bytes.toInt(), nanoTime)
        val request = Request.Builder()
            .url(endpoints.uploadUrl)
            .post(requestBody)
            .build()
        return execute(request, timeoutMs) { response ->
            if (!response.isSuccessful) return@execute failure(NetworkTransferFailure.HTTP)
            val startedAtNanos = requestBody.startedAtNanos
                ?: return@execute failure(NetworkTransferFailure.INCOMPLETE)
            val elapsedNanos = nanoTime() - startedAtNanos
            if (elapsedNanos <= 0L) return@execute failure(NetworkTransferFailure.INCOMPLETE)
            NetworkTransferResult.Success(NetworkTransferSample(bytes, elapsedNanos))
        }
    }

    private suspend fun execute(
        request: Request,
        timeoutMs: Long,
        readResponse: (Response) -> NetworkTransferResult
    ): NetworkTransferResult = suspendCancellableCoroutine { continuation ->
        val call = baseClient.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)

        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, exception: IOException) {
                if (continuation.isActive) {
                    continuation.resume(failure(classifyNetworkTransferFailure(exception)))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val sample = try {
                    response.use(readResponse)
                } catch (exception: Exception) {
                    failure(classifyNetworkTransferFailure(exception))
                }
                if (continuation.isActive) continuation.resume(sample)
            }
        })
    }

    private fun failure(reason: NetworkTransferFailure) =
        NetworkTransferResult.Failure(reason)

    private class TimedRequestBody(
        private val byteCount: Int,
        private val nanoTime: () -> Long
    ) : RequestBody() {
        @Volatile
        var startedAtNanos: Long? = null
            private set

        override fun contentType() = OCTET_STREAM

        override fun contentLength(): Long = byteCount.toLong()

        override fun writeTo(sink: BufferedSink) {
            val payload = ByteArray(byteCount) { 7 }
            startedAtNanos = nanoTime()
            sink.write(payload)
            sink.flush()
        }
    }

    private companion object {
        val OCTET_STREAM = "application/octet-stream".toMediaType()
        val DEFAULT_ENDPOINTS = NetworkSpeedEndpoints(
            downloadBaseUrl = "https://speed.cloudflare.com/__down".toHttpUrl(),
            uploadUrl = "https://speed.cloudflare.com/__up".toHttpUrl()
        )
        val DEFAULT_CLIENT = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}

internal fun classifyNetworkTransferFailure(exception: Throwable): NetworkTransferFailure =
    when (exception) {
        is UnknownHostException -> NetworkTransferFailure.DNS
        is ConnectException -> NetworkTransferFailure.CONNECT
        is SSLException -> NetworkTransferFailure.TLS
        is InterruptedIOException -> NetworkTransferFailure.TIMEOUT
        is IOException -> NetworkTransferFailure.IO
        else -> NetworkTransferFailure.IO
    }
