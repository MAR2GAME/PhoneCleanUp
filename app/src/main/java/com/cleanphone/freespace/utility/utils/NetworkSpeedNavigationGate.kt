package com.cleanphone.freespace.utility.utils

import com.cleanphone.freespace.utility.bean.NetworkSpeedResult
import com.cleanphone.freespace.utility.bean.isValidSpeed

internal class NetworkSpeedNavigationGate {
    private var pendingResult: NetworkSpeedResult? = null
    private var consumed = false

    fun submit(result: NetworkSpeedResult): Boolean {
        if (consumed || pendingResult != null) return false
        val normalized = NetworkSpeedResult(
            downloadMbps = result.downloadMbps.takeIf { it.isValidSpeed() },
            uploadMbps = result.uploadMbps.takeIf { it.isValidSpeed() }
        )
        if (!normalized.hasAnyMeasuredSpeed()) return false
        pendingResult = normalized
        return true
    }

    fun consumeIfResumed(isResumed: Boolean): NetworkSpeedResult? {
        if (!isResumed || consumed) return null
        val result = pendingResult ?: return null
        pendingResult = null
        consumed = true
        return result
    }
}
