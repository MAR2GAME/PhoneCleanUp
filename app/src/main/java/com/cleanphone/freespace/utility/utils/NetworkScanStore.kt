package com.cleanphone.freespace.utility.utils

import com.cleanphone.freespace.utility.bean.NetworkDeviceInfo
import com.cleanphone.freespace.utility.bean.NetworkScanSnapshot


object NetworkScanStore {
    private const val KEY_LAST_SCAN_TIME = "network_scan_last_scan_time"
    @Volatile
    private var lastSnapshot: NetworkScanSnapshot? = null

    fun getLastScanTime(): Long = AppPrefsUtils.getLong(KEY_LAST_SCAN_TIME)

    fun saveLastScanTime(timestamp: Long) {
        AppPrefsUtils.commitLong(KEY_LAST_SCAN_TIME, timestamp)
    }

    fun saveSnapshot(snapshot: NetworkScanSnapshot) {
        lastSnapshot = snapshot
        saveLastScanTime(snapshot.lastScanTime)
    }

    fun getLastSnapshot(): NetworkScanSnapshot? = lastSnapshot

    fun getLastDevices(): List<NetworkDeviceInfo> = lastSnapshot?.devices.orEmpty()
}
