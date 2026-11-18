package com.cleanphone.freespace.utility.advertise

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import com.mar2sdk.core.common.AppObs
import com.mar2sdk.core.notify.app.AppNotificationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the existing notification destinations and deletion scenes with the SDK's new APIs. */
object CommercialNotificationRouter {
    private const val TAG = "CommercialNotification"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val counts = mutableMapOf<AppObs.MediaCollection, Int>()
    private val refreshJobs = mutableMapOf<AppObs.MediaCollection, Job>()
    private var installed = false

    private val collections = mapOf(
        AppObs.MediaCollection.IMAGES to MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        AppObs.MediaCollection.VIDEOS to MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        AppObs.MediaCollection.AUDIO to MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        AppObs.MediaCollection.DOWNLOADS to MediaStore.Downloads.EXTERNAL_CONTENT_URI,
    )

    /** Call after Core.init. Reuse the SDK observers instead of registering another observer. */
    fun install(application: Application) {
        if (installed) return
        installed = true
        AppObs.addListener { event ->
            if (event is AppObs.Event.MediaChanged) {
                refreshCount(application, event.collection, detectDeletion = true)
            }
        }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = refreshBaseline(application)
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        refreshBaseline(application)
    }

    private fun refreshBaseline(application: Application) {
        collections.keys.forEach { refreshCount(application, it, detectDeletion = false) }
    }

    private fun refreshCount(
        application: Application,
        collection: AppObs.MediaCollection,
        detectDeletion: Boolean,
    ) {
        refreshJobs.remove(collection)?.cancel()
        refreshJobs[collection] = scope.launch {
            if (detectDeletion) delay(300)
            val count = withContext(Dispatchers.IO) {
                readCount(application, collections.getValue(collection))
            }
            val previous = counts[collection]
            if (count == null) {
                counts.remove(collection)
                return@launch
            }
            counts[collection] = count
            if (detectDeletion && previous != null && count < previous) {
                val scene = when (collection) {
                    AppObs.MediaCollection.IMAGES -> "delete_photos"
                    AppObs.MediaCollection.VIDEOS -> "delete_videos"
                    AppObs.MediaCollection.AUDIO, AppObs.MediaCollection.DOWNLOADS -> "delete_files"
                }
                try {
                    AppNotificationManager.addBatch(scene)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.w(TAG, "Unable to schedule notification scene $scene", exception)
                }
            }
        }
    }

    private fun readCount(application: Application, uri: Uri): Int? = try {
        application.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.count }
    } catch (exception: SecurityException) {
        // Permission can be granted later; the next resumed page establishes a fresh baseline.
        null
    } catch (exception: Exception) {
        Log.w(TAG, "Unable to inspect media collection $uri", exception)
        null
    }

    /** Run before the launcher's existing routing and click tracking read the notification extras. */
    fun normalizeIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("Route")?.let { rawRoute ->
            val route = rawRoute.trim().removePrefix("/").replace('-', '_').lowercase()
            val destination = when (route) {
                "action1" -> "junk_files"
                "action2" -> "large_files"
                "action3" -> "app_manager"
                "action4" -> "battery_info"
                "persistent" -> "home"
                "network_scan" -> "networkscan"
                "network_speed" -> "networkspeed"
                "network_usage" -> "networkusage"
                "app_usage" -> "appusage"
                else -> rawRoute
            }
            intent.putExtra("Route", destination)
        }
        if (!intent.hasExtra("id")) {
            (intent.getStringExtra("Scene") ?: intent.getStringExtra("scene"))
                ?.let { intent.putExtra("id", it) }
        }
    }
}
