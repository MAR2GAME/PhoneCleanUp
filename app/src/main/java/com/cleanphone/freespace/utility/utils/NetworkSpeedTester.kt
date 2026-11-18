package com.cleanphone.freespace.utility.utils

import com.cleanphone.freespace.utility.bean.NetworkSpeedResult
import com.cleanphone.freespace.utility.bean.SpeedTestStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val NANOS_PER_MILLISECOND = 1_000_000L

internal class NetworkSpeedTester(
    private val transport: NetworkSpeedTransport = OkHttpNetworkSpeedTransport(),
    private val nanoTime: () -> Long = System::nanoTime
) {
    suspend fun runTest(
        totalTimeoutMs: Long = DEFAULT_TOTAL_TIMEOUT_MS,
        onStageChanged: suspend (SpeedTestStage) -> Unit,
        onProgress: suspend (SpeedTestStage, Double) -> Unit,
        onFailure: suspend (SpeedTestStage, Int, NetworkTransferFailure) -> Unit = { _, _, _ -> }
    ): NetworkSpeedResult? = withContext(Dispatchers.IO) {
        require(totalTimeoutMs > 0) { "totalTimeoutMs must be positive" }

        val overallDeadlineNanos = nanoTime() + totalTimeoutMs * NANOS_PER_MILLISECOND
        val downloadBudgetMs = minOf(
            MAX_DIRECTION_BUDGET_MS,
            (totalTimeoutMs / 2).coerceAtLeast(1L)
        )

        onStageChanged(SpeedTestStage.DOWNLOADING)
        val downloadMbps = measureDirectionWithinBudget(
            timeoutMs = remainingTimeoutMs(overallDeadlineNanos)
                ?.coerceAtMost(downloadBudgetMs),
            measure = {
                measureDirection(
                    stage = SpeedTestStage.DOWNLOADING,
                    warmupBytes = DOWNLOAD_WARMUP_BYTES,
                    minimumBytes = DOWNLOAD_MIN_BYTES,
                    initialMaximumBytes = DOWNLOAD_MAX_BYTES,
                    maximumBytes = DOWNLOAD_MAX_BYTES,
                    directionDeadlineNanos = minOf(
                        overallDeadlineNanos,
                        nanoTime() + downloadBudgetMs * NANOS_PER_MILLISECOND
                    ),
                    transfer = transport::download,
                    onProgress = onProgress,
                    onFailure = onFailure
                )
            }
        )

        onStageChanged(SpeedTestStage.UPLOADING)
        val uploadBudgetMs = remainingTimeoutMs(overallDeadlineNanos)
            ?.coerceAtMost(MAX_DIRECTION_BUDGET_MS)
        val uploadMbps = measureDirectionWithinBudget(
            timeoutMs = uploadBudgetMs,
            measure = {
                measureDirection(
                    stage = SpeedTestStage.UPLOADING,
                    warmupBytes = UPLOAD_WARMUP_BYTES,
                    minimumBytes = UPLOAD_MIN_BYTES,
                    initialMaximumBytes = UPLOAD_INITIAL_MAX_BYTES,
                    maximumBytes = UPLOAD_MAX_BYTES,
                    directionDeadlineNanos = minOf(
                        overallDeadlineNanos,
                        nanoTime() + (uploadBudgetMs ?: 0L) * NANOS_PER_MILLISECOND
                    ),
                    transfer = transport::upload,
                    onProgress = onProgress,
                    onFailure = onFailure
                )
            }
        )

        NetworkSpeedResult(downloadMbps, uploadMbps)
            .takeIf { it.hasAnyMeasuredSpeed() }
    }

    private suspend fun measureDirectionWithinBudget(
        timeoutMs: Long?,
        measure: suspend () -> Double?
    ): Double? {
        if (timeoutMs == null || timeoutMs <= 0L) return null
        return withTimeoutOrNull(timeoutMs) { measure() }
    }

    private suspend fun measureDirection(
        stage: SpeedTestStage,
        warmupBytes: Long,
        minimumBytes: Long,
        initialMaximumBytes: Long,
        maximumBytes: Long,
        directionDeadlineNanos: Long,
        transfer: suspend (bytes: Long, timeoutMs: Long) -> NetworkTransferResult,
        onProgress: suspend (SpeedTestStage, Double) -> Unit,
        onFailure: suspend (SpeedTestStage, Int, NetworkTransferFailure) -> Unit
    ): Double? {
        val warmupTimeoutMs = remainingTimeoutMs(directionDeadlineNanos)
            ?.coerceAtMost(WARMUP_TIMEOUT_MS)
        val warmupResult = warmupTimeoutMs?.let { transfer(warmupBytes, it) }
        warmupResult.reportFailure(stage, WARMUP_ATTEMPT, onFailure)
        val warmupSample = warmupResult.successSampleOrNull()
        val warmupMbps = warmupSample?.toMbps()
        var payloadBytes = NetworkSpeedMeasurement.payloadBytes(
            estimatedMbps = warmupMbps,
            targetDurationMs = TARGET_SAMPLE_DURATION_MS,
            minimumBytes = minimumBytes,
            maximumBytes = initialMaximumBytes
        )

        val samples = mutableListOf<Double>()
        repeat(MAX_SAMPLE_ATTEMPTS) {
            if (samples.size >= DESIRED_SAMPLES) return@repeat
            val timeoutMs = remainingTimeoutMs(directionDeadlineNanos)
                ?: return@repeat
            val result = transfer(payloadBytes, timeoutMs)
            result.reportFailure(stage, it + 1, onFailure)
            val speedMbps = result
                .successSampleOrNull()
                ?.toMbps()
                ?: return@repeat
            samples += speedMbps
            onProgress(stage, speedMbps)
            payloadBytes = NetworkSpeedMeasurement.payloadBytes(
                estimatedMbps = speedMbps,
                targetDurationMs = TARGET_SAMPLE_DURATION_MS,
                minimumBytes = minimumBytes,
                maximumBytes = maximumBytes
            )
        }
        return NetworkSpeedMeasurement.median(samples, REQUIRED_SAMPLES)
    }

    private fun NetworkTransferSample.toMbps(): Double? =
        NetworkSpeedMeasurement.toMbps(bytes, elapsedNanos)

    private fun NetworkTransferResult?.successSampleOrNull(): NetworkTransferSample? =
        (this as? NetworkTransferResult.Success)?.sample

    private suspend fun NetworkTransferResult?.reportFailure(
        stage: SpeedTestStage,
        attempt: Int,
        onFailure: suspend (SpeedTestStage, Int, NetworkTransferFailure) -> Unit
    ) {
        val reason = (this as? NetworkTransferResult.Failure)?.reason ?: return
        onFailure(stage, attempt, reason)
    }

    private fun remainingTimeoutMs(deadlineNanos: Long): Long? {
        val remainingNanos = deadlineNanos - nanoTime()
        if (remainingNanos <= 0L) return null
        return ((remainingNanos + NANOS_PER_MILLISECOND - 1) / NANOS_PER_MILLISECOND)
            .coerceAtLeast(1L)
    }

    companion object {
        const val DEFAULT_TOTAL_TIMEOUT_MS = 15_000L

        suspend fun runTest(
            attemptTimeoutMs: Int,
            phaseTimeoutMs: Int,
            responseTimeoutMs: Int,
            onStageChanged: suspend (SpeedTestStage) -> Unit,
            onProgress: suspend (SpeedTestStage, Double) -> Unit
        ): NetworkSpeedResult {
            require(phaseTimeoutMs > 0) { "phaseTimeoutMs must be positive" }
            require(responseTimeoutMs > 0) { "responseTimeoutMs must be positive" }
            return NetworkSpeedTester().runTest(
                totalTimeoutMs = attemptTimeoutMs.toLong(),
                onStageChanged = onStageChanged,
                onProgress = onProgress
            ) ?: NetworkSpeedResult(null, null)
        }

        private const val TARGET_SAMPLE_DURATION_MS = 1_250L
        private const val WARMUP_TIMEOUT_MS = 2_000L
        private const val MAX_DIRECTION_BUDGET_MS = 7_000L
        private const val DESIRED_SAMPLES = 3
        private const val REQUIRED_SAMPLES = 1
        private const val MAX_SAMPLE_ATTEMPTS = 4
        private const val WARMUP_ATTEMPT = 0

        private const val DOWNLOAD_WARMUP_BYTES = 256L * 1024
        private const val DOWNLOAD_MIN_BYTES = 256L * 1024
        private const val DOWNLOAD_MAX_BYTES = 8L * 1024 * 1024
        private const val UPLOAD_WARMUP_BYTES = 64L * 1024
        private const val UPLOAD_MIN_BYTES = 64L * 1024
        private const val UPLOAD_INITIAL_MAX_BYTES = 64L * 1024
        private const val UPLOAD_MAX_BYTES = 2L * 1024 * 1024
    }
}

internal fun isSuccessfulNetworkSpeedTransfer(
    transferredBytes: Long,
    expectedBytes: Long,
    responseCode: Int?,
    completedBeforeDeadline: Boolean
): Boolean {
    return completedBeforeDeadline &&
        transferredBytes == expectedBytes &&
        responseCode in 200..299
}

internal fun networkSpeedPhaseDeadlineNanos(
    attemptDeadlineNanos: Long,
    phaseTimeoutMs: Int,
    nowNanos: Long = System.nanoTime()
): Long {
    require(phaseTimeoutMs > 0) { "phaseTimeoutMs must be positive" }
    val phaseDeadlineNanos = nowNanos + phaseTimeoutMs * NANOS_PER_MILLISECOND
    return minOf(attemptDeadlineNanos, phaseDeadlineNanos)
}

internal fun networkSpeedResponseDeadlineNanos(
    attemptDeadlineNanos: Long,
    responseTimeoutMs: Int,
    nowNanos: Long = System.nanoTime()
): Long {
    require(responseTimeoutMs > 0) { "responseTimeoutMs must be positive" }
    val responseDeadlineNanos = nowNanos + responseTimeoutMs * NANOS_PER_MILLISECOND
    return minOf(attemptDeadlineNanos, responseDeadlineNanos)
}
