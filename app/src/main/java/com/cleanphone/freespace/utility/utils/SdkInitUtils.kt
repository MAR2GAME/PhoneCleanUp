package com.cleanphone.freespace.utility.utils

import android.app.Application
import android.util.Log
import com.cleanphone.freespace.utility.BuildConfig
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.advertise.CommercialNotificationRouter
import com.mar2sdk.core.AppMod
import com.mar2sdk.core.Core
import com.mar2sdk.core.ad.impl.admob.AdmobConfig
import com.mar2sdk.core.ad.impl.admob.NativeAdConfig
import com.mar2sdk.impl.AppOpenAdManager
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object SdkInitUtils {
    private var initializationJob: CompletableJob? = null
    private var initialized = false

    // Initialize lifecycle observers and platform SDKs before the first Activity.
    fun initSDK(context: Application) {
        if (initializationJob != null) return
        val job = Job()
        initializationJob = job
        try {
            Core.init(context, if (BuildConfig.DEBUG) AppMod.DEBUG else AppMod.RELEASE)
            applyAdUnitOverrides()
            CommercialNotificationRouter.install(context)
            AppOpenAdManager.init(context)
            initialized = true
        } catch (exception: Exception) {
            Log.e("SdkInit", "Mar2SDK initialization failed", exception)
        } finally {
            job.complete()
        }
    }

    // Local/Gradle overrides retain the existing ad IDs without overwriting saved config.
    private fun applyAdUnitOverrides() {
        val defaults = Core.app.resources.openRawResource(R.raw.admob_config)
            .bufferedReader().use { JSONObject(it.readText()) }
        if (BuildConfig.ADV_ADMOB_OPEN_ID != defaults.getJSONObject("openConfig").getString("id")) {
            AdmobConfig.openConfig = AdmobConfig.openConfig.copy(id = BuildConfig.ADV_ADMOB_OPEN_ID)
        }
        if (BuildConfig.ADV_ADMOB_INTERSTITIAL_ID != defaults.getJSONObject("interConfig").getString("id")) {
            AdmobConfig.interConfig = AdmobConfig.interConfig.copy(id = BuildConfig.ADV_ADMOB_INTERSTITIAL_ID)
        }
        if (BuildConfig.ADV_ADMOB_REWARDED_ID != defaults.getJSONObject("videoConfig").getString("id")) {
            val id = BuildConfig.ADV_ADMOB_REWARDED_ID
            AdmobConfig.videoConfig = AdmobConfig.videoConfig.copy(id = id, poolSize = if (id.isBlank()) 0 else 1)
        }
        if (BuildConfig.ADV_ADMOB_BANNER_ID != defaults.getJSONObject("bannerConfig").getString("id")) {
            AdmobConfig.bannerConfig = AdmobConfig.bannerConfig.copy(id = BuildConfig.ADV_ADMOB_BANNER_ID)
        }
        val nativeId = BuildConfig.ADV_ADMOB_NATIVE_ID
        if (nativeId.isNotBlank()) {
            AdmobConfig.nativeConfig = AdmobConfig.nativeConfig.copy(
                ads = listOf(NativeAdConfig(nativeId, nativeId, nativeId)),
            )
        }
    }

    suspend fun awaitInitialization(timeoutMs: Long? = null) {
        val job = initializationJob ?: return
        if (timeoutMs == null) job.join() else withTimeoutOrNull(timeoutMs) { job.join() }
    }

    fun isInitialized(): Boolean = initialized

    fun cleanup() {
        initializationJob?.cancel()
        initializationJob = null
        initialized = false
    }
}
