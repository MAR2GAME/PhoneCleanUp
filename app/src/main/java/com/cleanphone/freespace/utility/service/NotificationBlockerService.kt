package com.cleanphone.freespace.utility.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cleanphone.freespace.utility.bean.NotificationBarRecord
import com.cleanphone.freespace.utility.utils.NotificationBarStore


class NotificationBlockerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationBarStore.refresh(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationBarStore.refresh(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || shouldIgnoreNotification(sbn)) return
        if (!NotificationBarStore.shouldBlockPackage(sbn.packageName)) return

        NotificationBarStore.recordBlockedNotification(
            context = this,
            record = createBlockedRecord(sbn)
        )

        runCatching { cancelNotification(sbn.key) }
    }

    private fun shouldIgnoreNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return true
        if (!sbn.isClearable) return true
        return false
    }

    private fun createBlockedRecord(sbn: StatusBarNotification): NotificationBarRecord {
        val extras = sbn.notification.extras
        val appName = runCatching {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(sbn.packageName)

        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        return NotificationBarRecord(
            notificationKey = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title.ifBlank { appName },
            text = text,
            postedAt = sbn.postTime
        )
    }
}
