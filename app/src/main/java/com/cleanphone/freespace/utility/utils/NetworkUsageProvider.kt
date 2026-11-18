package com.cleanphone.freespace.utility.utils

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import com.cleanphone.freespace.utility.bean.NetworkUsageEntry
import com.cleanphone.freespace.utility.bean.NetworkUsageSnapshot
import com.cleanphone.freespace.utility.bean.NetworkUsageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object NetworkUsageProvider {
    suspend fun loadSnapshot(
        context: Context,
        usageType: NetworkUsageType
    ): NetworkUsageSnapshot = withContext(Dispatchers.IO) {
        val networkStatsManager = context.getSystemService(NetworkStatsManager::class.java)
        val packageManager = context.packageManager

        val networkType = when (usageType) {
            NetworkUsageType.CELLULAR -> ConnectivityManager.TYPE_MOBILE
            NetworkUsageType.WIFI -> ConnectivityManager.TYPE_WIFI
        }
        val subscriberId: String? = null

        val (startTime, endTime) = buildTodayRange()

        val installedApps = packageManager.getInstalledApplications(0)
        val entries = installedApps.mapNotNull { appInfo ->
            val bucket = queryUsageBucket(
                networkStatsManager = networkStatsManager,
                networkType = networkType,
                subscriberId = subscriberId,
                startTime = startTime,
                endTime = endTime,
                uid = appInfo.uid
            ) ?: return@mapNotNull null

            val totalBytes = bucket.rxBytes + bucket.txBytes
            if (totalBytes <= 0L) return@mapNotNull null

            NetworkUsageEntry(
                packageName = appInfo.packageName,
                appName = packageManager.getApplicationLabel(appInfo).toString(),
                icon = runCatching { packageManager.getApplicationIcon(appInfo) }.getOrNull(),
                totalBytes = totalBytes,
                rxBytes = bucket.rxBytes,
                txBytes = bucket.txBytes
            )
        }.sortedByDescending { it.totalBytes }

        NetworkUsageSnapshot(
            usageType = usageType,
            totalBytes = entries.sumOf { it.totalBytes },
            rxBytes = entries.sumOf { it.rxBytes },
            txBytes = entries.sumOf { it.txBytes },
            entries = entries
        )
    }

    private fun queryUsageBucket(
        networkStatsManager: NetworkStatsManager,
        networkType: Int,
        subscriberId: String?,
        startTime: Long,
        endTime: Long,
        uid: Int
    ): UidTraffic? {
        return runCatching {
            val stats = networkStatsManager.queryDetailsForUidTagState(
                networkType,
                subscriberId,
                startTime,
                endTime,
                uid,
                NetworkStats.Bucket.TAG_NONE,
                NetworkStats.Bucket.STATE_ALL
            )
            val current = NetworkStats.Bucket()
            var totalRx = 0L
            var totalTx = 0L
            var hasData = false
            while (stats.hasNextBucket()) {
                stats.getNextBucket(current)
                totalRx += current.rxBytes
                totalTx += current.txBytes
                hasData = true
            }
            stats.close()
            if (hasData) {
                UidTraffic(
                    rxBytes = totalRx,
                    txBytes = totalTx
                )
            } else {
                null
            }
        }.getOrNull()
    }

    private fun buildTodayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis to System.currentTimeMillis()
    }
    private data class UidTraffic(
        val rxBytes: Long,
        val txBytes: Long
    )
}
