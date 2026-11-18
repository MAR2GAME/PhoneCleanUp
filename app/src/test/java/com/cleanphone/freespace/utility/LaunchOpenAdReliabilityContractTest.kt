package com.cleanphone.freespace.utility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LaunchOpenAdReliabilityContractTest {
    @Test
    fun `launch preload is consent gated and keeps startup trigger`() {
        val mediator = File(
            "src/main/java/com/cleanphone/freespace/utility/advertise/AdvertisePageMediator.kt",
        ).readText()

        assertTrue(mediator.contains("fun canRequestAds(context: Context): Boolean"))
        assertTrue(mediator.contains(".canRequestAds()"))
        assertTrue(
            mediator.contains(
                "AdvertiseSdk.preloadOpen(context, AdvertiseSdk.LOAD_TIME_OPEN_APP)",
            ),
        )
    }

    @Test
    fun `splash uses bounded retry scheduler instead of one shot retry`() {
        val splash = File(
            "src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt",
        ).readText()

        assertTrue(splash.contains("LaunchOpenAdPreloadScheduler"))
        assertTrue(splash.contains("tryBeginLaunchOpenAdPreload()"))
        assertFalse(splash.contains("requestedRetryPreload"))
        assertTrue(splash.contains("OPEN_AD_LOAD_WAIT_TIMEOUT_MS = 12_000L"))
        assertTrue(splash.contains("OPEN_AD_OFFLINE_LOAD_WAIT_TIMEOUT_MS = 2_500L"))
    }
}
