package com.cleanphone.freespace.utility.utils

import com.cleanphone.freespace.utility.bean.NotificationBarRecord

object NotificationBarStateReducer {
    fun toggleBlockedPackage(
        currentPackages: Set<String>,
        packageName: String
    ): Set<String> {
        val updatedPackages = currentPackages.toMutableSet()
        if (updatedPackages.contains(packageName)) {
            updatedPackages.remove(packageName)
        } else {
            updatedPackages.add(packageName)
        }
        return updatedPackages
    }

    fun addBlockedRecord(
        currentRecords: List<NotificationBarRecord>,
        newRecord: NotificationBarRecord,
        limit: Int
    ): List<NotificationBarRecord> {
        if (limit <= 0) return emptyList()
        return (listOf(newRecord) + currentRecords)
            .distinctBy { it.notificationKey }
            .take(limit)
    }

    fun clearBlockedRecords(currentRecords: List<NotificationBarRecord>): Pair<Int, List<NotificationBarRecord>> {
        return currentRecords.size to emptyList()
    }
}
