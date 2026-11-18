package com.cleanphone.freespace.utility.utils

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cleanphone.freespace.utility.BuildConfig
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.advertise.AdvertiseConfigFactory
import com.cleanphone.freespace.utility.advertise.AdvertiseRuntimeCapabilities
import com.pdffox.adv.notification.NotificationManager as AdvertiseNotificationManager
import com.pdffox.adv.AdvertiseSdk
import com.pdffox.adv.notification.NotificationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

object SdkInitUtils {
    private const val TAG = "SdkInit"

    private val isInitializing = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private var initializationJob: Job? = null
    private val initializationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initSDKAsync(context: Application): Job {
        if (isInitializing.getAndSet(true)) {
            return initializationJob ?: Job().apply { complete() }
        }
        initializationJob = initializationScope.launch {
            try {
                AdvertiseSdk.init(
                    context = context,
                    isTest = BuildConfig.DEBUG,
                    sdkConfig = AdvertiseConfigFactory.create(context),
                )
                loadLocalNotificationContentIfNeeded(context)
                isInitialized.set(true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Advertise SDK initialization failed; continue app startup", exception)
                isInitialized.set(false)
            } finally {
                isInitializing.set(false)
            }
        }
        return initializationJob!!
    }


    private fun loadLocalNotificationContentIfNeeded(context: Application) {
        if (!AdvertiseRuntimeCapabilities.NOTIFICATIONS_ENABLED) return
        runCatching {
            context.resources.openRawResource(R.raw.notification_content)
                .bufferedReader()
                .use { it.readText() }
        }.onSuccess { content ->
            if (content.isNotBlank()) {
                AdvertiseNotificationManager.updateNotificationContent(content)
                Log.i(TAG, "Local notification_content loaded")
            }
        }.onFailure {
            Log.e(TAG, "Local notification_content load failed", it)
        }
    }

    suspend fun awaitInitialization(timeoutMs: Long? = null) {
        val job = initializationJob ?: return
        if (timeoutMs == null) {
            job.join()
        } else {
            withTimeoutOrNull(timeoutMs) { job.join() }
        }
    }



    fun isInitialized(): Boolean = isInitialized.get()

    fun cleanup() {
        initializationJob?.cancel()
        initializationScope.cancel()
        initializationJob = null
        isInitialized.set(false)
        isInitializing.set(false)
    }
}
