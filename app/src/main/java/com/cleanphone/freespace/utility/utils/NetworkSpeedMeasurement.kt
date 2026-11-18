package com.cleanphone.freespace.utility.utils

internal object NetworkSpeedMeasurement {
    private const val NANOS_PER_SECOND = 1_000_000_000.0

    fun toMbps(bytes: Long, elapsedNanos: Long): Double? {
        if (bytes <= 0 || elapsedNanos <= 0) return null
        return (bytes * 8.0 * NANOS_PER_SECOND / elapsedNanos / 1_000_000.0)
            .takeIf { it.isFinite() && it > 0.0 }
    }

    fun payloadBytes(
        estimatedMbps: Double?,
        targetDurationMs: Long,
        minimumBytes: Long,
        maximumBytes: Long
    ): Long {
        require(targetDurationMs > 0) { "targetDurationMs must be positive" }
        require(minimumBytes > 0) { "minimumBytes must be positive" }
        require(maximumBytes >= minimumBytes) { "maximumBytes must not be smaller than minimumBytes" }

        val estimate = estimatedMbps?.takeIf { it.isFinite() && it > 0.0 }
            ?: return minimumBytes
        val bytes = estimate * 1_000_000.0 / 8.0 * targetDurationMs / 1_000.0
        return bytes.toLong().coerceIn(minimumBytes, maximumBytes)
    }

    fun median(values: List<Double>, minimumSamples: Int = 2): Double? {
        require(minimumSamples > 0) { "minimumSamples must be positive" }
        val sorted = values.filter { it.isFinite() && it > 0.0 }.sorted()
        if (sorted.size < minimumSamples) return null

        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }
}
