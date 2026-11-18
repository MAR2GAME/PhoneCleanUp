package com.cleanphone.freespace.utility.advertise

import org.junit.Assert.assertEquals
import org.junit.Test

class InterstitialAreaResolverTest {
    private val labels = FeatureEntryLabels(
        removeJunk = "Remove junk",
        notificationBar = "Notification bar",
        appUsage = "App usage",
        networkScan = "Network scan",
        networkSpeed = "Network speed",
        networkUsage = "Network usage",
        processManager = "Process manager",
        appManager = "App manager",
        batteryInfo = "Battery info",
        speakerCleaner = "Speaker cleaner",
        images = "Images",
        videos = "Videos",
        audio = "Audio",
        apk = "APK",
        largeFiles = "Large files",
        screenshots = "Screenshots",
    )

    @Test
    fun featureLabelsResolveToTheirOwnAreaKeys() {
        assertEquals(AdAreaKeys.Interstitial.ENTER_JUNK_CLEAN, resolve(labels.removeJunk))
        assertEquals(AdAreaKeys.Interstitial.ENTER_NOTIFICATION_CLEAN, resolve(labels.notificationBar))
        assertEquals(AdAreaKeys.Interstitial.ENTER_APP_USAGE, resolve(labels.appUsage))
        assertEquals(AdAreaKeys.Interstitial.ENTER_NETWORK_SCAN, resolve(labels.networkScan))
        assertEquals(AdAreaKeys.Interstitial.ENTER_NETWORK_SPEED, resolve(labels.networkSpeed))
        assertEquals(AdAreaKeys.Interstitial.ENTER_NETWORK_USAGE, resolve(labels.networkUsage))
        assertEquals(AdAreaKeys.Interstitial.ENTER_PROCESS_MANAGE, resolve(labels.processManager))
        assertEquals(AdAreaKeys.Interstitial.ENTER_APP_MANAGE, resolve(labels.appManager))
        assertEquals(AdAreaKeys.Interstitial.ENTER_BATTERY_INFO, resolve(labels.batteryInfo))
        assertEquals(AdAreaKeys.Interstitial.ENTER_SPEAKER_CLEAN, resolve(labels.speakerCleaner))
    }

    @Test
    fun fileCategoriesAndUnknownLabelsUseFileManagerArea() {
        listOf(
            labels.images,
            labels.videos,
            labels.audio,
            labels.apk,
            labels.largeFiles,
            labels.screenshots,
            "unexpected label",
        ).forEach { tag ->
            assertEquals(AdAreaKeys.Interstitial.ENTER_FILE_MANAGE, resolve(tag))
        }
    }

    @Test
    fun areaKeysAreForwardedWithoutPageLabelMapping() {
        assertEquals(
            AdAreaKeys.Interstitial.ENTER_PROCESS_MANAGE,
            InterstitialAreaResolver.forwardAreaKey(AdAreaKeys.Interstitial.ENTER_PROCESS_MANAGE),
        )
    }

    private fun resolve(tag: String): String = InterstitialAreaResolver.featureEntry(labels, tag)
}
