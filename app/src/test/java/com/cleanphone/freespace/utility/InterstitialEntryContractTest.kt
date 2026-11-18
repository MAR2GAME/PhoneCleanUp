package com.cleanphone.freespace.utility

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InterstitialEntryContractTest {
    @Test
    fun areaKeyCallersUseExplicitAreaKeyApi() {
        val mediator = File(
            "src/main/java/com/cleanphone/freespace/utility/advertise/AdvertisePageMediator.kt",
        ).readText()
        val toolbox = File(
            "src/main/java/com/cleanphone/freespace/utility/fragment/ToolBoxFragment.kt",
        ).readText()
        val taskList = File(
            "src/main/java/com/cleanphone/freespace/utility/ui/TaskListActivity.kt",
        ).readText()

        assertTrue(mediator.contains("showFeatureEntryInterstitialByTag"))
        assertTrue(mediator.contains("showFeatureEntryInterstitialByAreaKey"))
        assertTrue(mediator.contains("showInterstitial(activity, areaKey"))
        assertTrue(toolbox.contains("showFeatureEntryInterstitialByAreaKey"))
        assertTrue(!toolbox.contains("showFeatureEntryInterstitial(requireActivity(),AdAreaKeys"))
        assertTrue(taskList.contains("showFeatureEntryInterstitialByAreaKey"))
    }

    @Test
    fun interstitialWaitsForSdkInitializationWithoutBlockingMainThread() {
        val mediator = File(
            "src/main/java/com/cleanphone/freespace/utility/advertise/AdvertisePageMediator.kt",
        ).readText()

        assertTrue(mediator.contains("lifecycleScope.launch"))
        assertTrue(mediator.contains("SdkInitUtils.awaitInitialization"))
        assertTrue(mediator.contains("SdkInitUtils.isInitialized"))
        assertTrue(mediator.contains("SDK_INITIALIZATION_WAIT_TIMEOUT_MS"))
    }
}
