package com.cleanphone.freespace.utility.bean

import android.graphics.drawable.Drawable

data class NotificationBarRecord(
    val notificationKey: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

data class NotificationAppEntry(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

data class NotificationDashboardState(
    val listenerEnabled: Boolean = false,
    val blockingEnabled: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
    val blockedRecords: List<NotificationBarRecord> = emptyList(),
    val totalBlockedCount: Int = 0
) {
    val hasConfiguredTargets: Boolean
        get() = blockedPackages.isNotEmpty()

    val hasBlockedNotifications: Boolean
        get() = blockedRecords.isNotEmpty()
}
