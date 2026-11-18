package com.cleanphone.freespace.utility.bean

data class NetworkInfoSnapshot(
    val networkType: String,
    val wifiName: String,
    val ipAddress: String
)

data class NetworkSpeedResult(
    val downloadMbps: Double?,
    val uploadMbps: Double?
) {
    fun hasAnyMeasuredSpeed(): Boolean =
        downloadMbps.isValidSpeed() || uploadMbps.isValidSpeed()

    fun hasMeasuredSpeed(): Boolean = hasAnyMeasuredSpeed()
}

internal fun Double?.isValidSpeed(): Boolean =
    this != null && isFinite() && this > 0.0

enum class SpeedTestStage {
    IDLE,
    DOWNLOADING,
    UPLOADING,
    COMPLETED
}
