package com.cleanphone.freespace.utility.bean

import android.graphics.drawable.Drawable

enum class NetworkUsageType {
    CELLULAR,
    WIFI
}

data class NetworkUsageEntry(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val totalBytes: Long,
    val rxBytes: Long,
    val txBytes: Long
)

data class NetworkUsageSnapshot(
    val usageType: NetworkUsageType,
    val totalBytes: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val entries: List<NetworkUsageEntry>
)
