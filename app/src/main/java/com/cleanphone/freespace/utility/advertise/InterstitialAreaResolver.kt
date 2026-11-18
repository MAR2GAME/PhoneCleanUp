package com.cleanphone.freespace.utility.advertise

data class FeatureEntryLabels(
    val removeJunk: String,
    val notificationBar: String,
    val appUsage: String,
    val networkScan: String,
    val networkSpeed: String,
    val networkUsage: String,
    val processManager: String,
    val appManager: String,
    val batteryInfo: String,
    val speakerCleaner: String,
    val images: String,
    val videos: String,
    val audio: String,
    val apk: String,
    val largeFiles: String,
    val screenshots: String,
)

object InterstitialAreaResolver {
    fun featureEntry(labels: FeatureEntryLabels, nextTag: String): String = when (nextTag) {
        labels.removeJunk -> AdAreaKeys.Interstitial.ENTER_JUNK_CLEAN
        labels.notificationBar -> AdAreaKeys.Interstitial.ENTER_NOTIFICATION_CLEAN
        labels.appUsage -> AdAreaKeys.Interstitial.ENTER_APP_USAGE
        labels.networkScan -> AdAreaKeys.Interstitial.ENTER_NETWORK_SCAN
        labels.networkSpeed -> AdAreaKeys.Interstitial.ENTER_NETWORK_SPEED
        labels.networkUsage -> AdAreaKeys.Interstitial.ENTER_NETWORK_USAGE
        labels.processManager -> AdAreaKeys.Interstitial.ENTER_PROCESS_MANAGE
        labels.appManager -> AdAreaKeys.Interstitial.ENTER_APP_MANAGE
        labels.batteryInfo -> AdAreaKeys.Interstitial.ENTER_BATTERY_INFO
        labels.speakerCleaner -> AdAreaKeys.Interstitial.ENTER_SPEAKER_CLEAN
        labels.images,
        labels.videos,
        labels.audio,
        labels.apk,
        labels.largeFiles,
        labels.screenshots,
        -> AdAreaKeys.Interstitial.ENTER_FILE_MANAGE
        else -> AdAreaKeys.Interstitial.ENTER_FILE_MANAGE
    }

    fun forwardAreaKey(areaKey: String): String = areaKey
}
