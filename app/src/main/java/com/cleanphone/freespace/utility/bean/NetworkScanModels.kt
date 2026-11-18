package com.cleanphone.freespace.utility.bean

import androidx.annotation.DrawableRes

data class NetworkScanDetail(
    val title: String,
    val value: String,
    @DrawableRes val statusIconResId: Int
)

data class NetworkDeviceInfo(
    val ipAddress: String,
    val macAddress: String,
    val interfaceName: String,
    val role: String,
    val isReachable: Boolean
)

data class NetworkScanSnapshot(
    val ssid: String,
    val lastScanTime: Long,
    val details: List<NetworkScanDetail>,
    val isConnectedToWifi: Boolean,
    val devices: List<NetworkDeviceInfo>
)
