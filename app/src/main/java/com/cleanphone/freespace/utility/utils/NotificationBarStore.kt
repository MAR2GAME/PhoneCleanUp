package com.cleanphone.freespace.utility.utils

import android.content.Context
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cleanphone.freespace.utility.bean.NotificationBarRecord
import com.cleanphone.freespace.utility.bean.NotificationDashboardState

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object NotificationBarStore {
    const val MAX_BLOCKED_RECORDS = 100

    private const val KEY_BLOCKING_ENABLED = "notification_bar_blocking_enabled"
    private const val KEY_BLOCKED_PACKAGES = "notification_bar_blocked_packages"
    private const val KEY_BLOCKED_RECORDS = "notification_bar_blocked_records"
    private const val KEY_TOTAL_BLOCKED_COUNT = "notification_bar_total_blocked_count"

    private val gson = Gson()
    private val blockedPackagesType = object : TypeToken<Set<String>>() {}.type
    private val blockedRecordsType = object : TypeToken<List<NotificationBarRecord>>() {}.type

    private val dashboardState = MutableLiveData(NotificationDashboardState())

    fun observeState(): LiveData<NotificationDashboardState> = dashboardState

    fun refresh(context: Context) {
        val state = loadState(context)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dashboardState.value = state
        } else {
            dashboardState.postValue(state)
        }
    }

    fun loadState(context: Context): NotificationDashboardState {
        return NotificationDashboardState(
            listenerEnabled = NotificationAccessUtils.isNotificationListenerEnabled(context),
            blockingEnabled = isBlockingEnabled(),
            blockedPackages = loadBlockedPackages(),
            blockedRecords = loadBlockedRecords(),
            totalBlockedCount = AppPrefsUtils.getInt(KEY_TOTAL_BLOCKED_COUNT)
        )
    }

    fun isBlockingEnabled(): Boolean = AppPrefsUtils.getBoolean(KEY_BLOCKING_ENABLED)

    fun setBlockingEnabled(context: Context, enabled: Boolean) {
        AppPrefsUtils.commitBoolean(KEY_BLOCKING_ENABLED, enabled)
        refresh(context)
    }

    fun loadBlockedPackages(): Set<String> {
        val json = AppPrefsUtils.getString(KEY_BLOCKED_PACKAGES)
        if (json.isBlank()) return emptySet()
        return runCatching { gson.fromJson<Set<String>>(json, blockedPackagesType) ?: emptySet() }
            .getOrDefault(emptySet())
    }

    fun toggleBlockedPackage(context: Context, packageName: String) {
        val updatedPackages = NotificationBarStateReducer.toggleBlockedPackage(
            currentPackages = loadBlockedPackages(),
            packageName = packageName
        )
        AppPrefsUtils.commitString(KEY_BLOCKED_PACKAGES, gson.toJson(updatedPackages))
        refresh(context)
    }

    fun shouldBlockPackage(packageName: String): Boolean {
        return isBlockingEnabled() && loadBlockedPackages().contains(packageName)
    }

    fun loadBlockedRecords(): List<NotificationBarRecord> {
        val json = AppPrefsUtils.getString(KEY_BLOCKED_RECORDS)
        if (json.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<NotificationBarRecord>>(json, blockedRecordsType) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun recordBlockedNotification(context: Context, record: NotificationBarRecord) {
        val updatedRecords = NotificationBarStateReducer.addBlockedRecord(
            currentRecords = loadBlockedRecords(),
            newRecord = record,
            limit = MAX_BLOCKED_RECORDS
        )
        AppPrefsUtils.commitString(KEY_BLOCKED_RECORDS, gson.toJson(updatedRecords))
        AppPrefsUtils.commitInt(
            KEY_TOTAL_BLOCKED_COUNT,
            AppPrefsUtils.getInt(KEY_TOTAL_BLOCKED_COUNT) + 1
        )
        refresh(context)
    }

    fun clearBlockedNotifications(context: Context): Int {
        val (clearedCount, clearedRecords) = NotificationBarStateReducer.clearBlockedRecords(loadBlockedRecords())
        AppPrefsUtils.commitString(KEY_BLOCKED_RECORDS, gson.toJson(clearedRecords))
        AppPrefsUtils.commitInt(KEY_TOTAL_BLOCKED_COUNT, 0)
        refresh(context)
        return clearedCount
    }
}
