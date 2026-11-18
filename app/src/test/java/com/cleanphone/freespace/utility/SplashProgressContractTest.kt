package com.cleanphone.freespace.utility

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SplashProgressContractTest {
    private val splashSource = File(
        "src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt",
    ).readText()

    @Test
    fun `splash uses short smooth visual progress phases`() {
        assertTrue(splashSource.contains("private const val PROGRESS_UPDATE_INTERVAL = 16L"))
        assertTrue(splashSource.contains("private const val PROGRESS_SCALE = 100"))
        assertTrue(splashSource.contains("private const val TOTAL_PROGRESS = 100 * PROGRESS_SCALE"))
        assertTrue(splashSource.contains("private const val VISUAL_PROGRESS_TARGET = 90 * PROGRESS_SCALE"))
        assertTrue(splashSource.contains("private const val STARTUP_PROGRESS_DURATION_MS = 4_000L"))
        assertTrue(splashSource.contains("private const val WAITING_PROGRESS_DURATION_MS = 8_000L"))
        assertTrue(splashSource.contains("PREPARATION_PROGRESS_MAX"))

        val splashLayout = File("src/main/res/layout/activity_splash.xml").readText()
        assertTrue(splashLayout.contains("android:max=\"10000\""))
    }

    @Test
    fun `smooth progress keeps ad wait and final completion behavior`() {
        assertTrue(splashSource.contains("OPEN_AD_LOAD_WAIT_TIMEOUT_MS = 10_000L"))
        assertTrue(splashSource.contains("OPEN_AD_OFFLINE_LOAD_WAIT_TIMEOUT_MS = 2_500L"))
        assertTrue(splashSource.contains("completeProgressQuickly()"))
        assertTrue(splashSource.contains("AdvertisePageMediator.showCachedLaunchOpenAd"))
    }
}
