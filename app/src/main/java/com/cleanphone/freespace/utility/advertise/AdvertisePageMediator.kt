package com.cleanphone.freespace.utility.advertise

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.ui.SplashActivity
import com.cleanphone.freespace.utility.utils.SdkInitUtils
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.mar2sdk.core.AppStatus
import com.mar2sdk.core.Core
import com.mar2sdk.core.ad.AdConfig
import com.mar2sdk.core.ad.AdLoader
import com.mar2sdk.core.ad.ConsentFlow
import com.mar2sdk.core.ad.policy.ScreenAdContext
import com.mar2sdk.core.ad.policy.ScreenAdTrigger
import com.mar2sdk.core.ad.status.AdFormat
import com.mar2sdk.core.log.LogAdParam
import com.mar2sdk.core.log.LogUtil
import com.mar2sdk.impl.AdActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

enum class LaunchAdsPreloadResult {
    REQUESTED,
    CONSENT_UNAVAILABLE,
    POLICY_DISABLED,
    FAILED,
}

object AdvertisePageMediator {
    private const val TAG = "AdvertisePageMediator"
    private const val SDK_INITIALIZATION_WAIT_TIMEOUT_MS = 8_000L
    private val foregroundOpportunity = ForegroundAdOpportunity()
    private var foregroundHost: WeakReference<Activity>? = null
    private var foregroundHostResumed = false
    private var processObserverRegistered = false
    private var foregroundRequest: Job? = null
    private var fullScreenRequestCount = 0

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            foregroundOpportunity.onForeground(AppStatus.isShowingAd || fullScreenRequestCount > 0)
            tryShowForegroundAd()
        }

        override fun onStop(owner: LifecycleOwner) {
            foregroundOpportunity.onBackground(AppStatus.isShowingAd || fullScreenRequestCount > 0)
        }
    }

    fun initSplashConsent(
        activity: Activity,
        flow: ConsentFlow? = null,
        onComplete: (Boolean) -> Unit,
    ) {
        if (activity.isUnavailable()) {
            onComplete(false)
            return
        }
        val completed = AtomicBoolean(false)
        fun completeOnce(success: Boolean) {
            if (completed.compareAndSet(false, true)) onComplete(success)
        }
        runCatching {
            val cached = if (flow == null) {
                Core.initConsent(activity, ::completeOnce)
            } else {
                Core.initConsent(activity, flow, ::completeOnce)
            }
            if (cached) completeOnce(true)
        }.onFailure { throwable ->
            Log.w(TAG, "initSplashConsent failed", throwable)
            completeOnce(false)
        }
    }

    fun showSplashConsent(
        activity: Activity,
        flow: ConsentFlow? = null,
        onWillShowForm: (() -> Boolean)? = null,
        onError: ((Throwable) -> Unit)? = null,
        onComplete: () -> Unit,
    ) {
        val completed = AtomicBoolean(false)
        fun completeOnce() {
            if (completed.compareAndSet(false, true)) onComplete()
        }
        if (activity.isUnavailable()) {
            completeOnce()
            return
        }
        runCatching {
            val consent = UserMessagingPlatform.getConsentInformation(activity)
            if (consent.isConsentFormAvailable &&
                consent.consentStatus == ConsentInformation.ConsentStatus.REQUIRED &&
                onWillShowForm?.invoke() == false
            ) {
                completeOnce()
                return@runCatching
            }
            if (flow == null) Core.showSplashConsent(activity, ::completeOnce)
            else Core.showSplashConsent(activity, flow, ::completeOnce)
        }.onFailure { throwable ->
            Log.w(TAG, "showSplashConsent failed", throwable)
            onError?.invoke(throwable)
                ?: LogUtil.log("ump_consent_error", mapOf("error" to (throwable.message ?: "")))
            completeOnce()
        }
    }

    fun showCompletionInterstitial(activity: Activity, nextTag: String, onClosed: () -> Unit) {
        val areaKey = completionInterstitialAreaKey(activity, nextTag)
        showInterstitial(activity, areaKey, onClosed)
    }

    fun showNotificationCleanFinishInterstitial(activity: Activity, onClosed: () -> Unit) {
        showInterstitial(activity, AdAreaKeys.Interstitial.NOTIFICATION_CLEAN_FINISH, onClosed)
    }


    fun showNetworkSpeedFinishInterstitial(activity: Activity, onClosed: () -> Unit) {
        showInterstitial(activity, AdAreaKeys.Interstitial.NETWORK_SPEED_FINISH, onClosed)
    }

    private fun completionInterstitialAreaKey(context: Context, nextTag: String): String? {
        return when (nextTag) {
            context.getString(R.string.remove_junk) -> AdAreaKeys.Interstitial.JUNK_CLEAN_FINISH
            context.getString(R.string.process_manager) -> AdAreaKeys.Interstitial.PROCESS_MANAGE_FINISH
            context.getString(R.string.app_manager) -> AdAreaKeys.Interstitial.APP_MANAGE_FINISH
            context.getString(R.string.battery_info) -> AdAreaKeys.Interstitial.BATTERY_INFO_FINISH
            context.getString(R.string.speaker_cleaner) -> AdAreaKeys.Interstitial.SPEAKER_CLEAN_FINISH
            context.getString(R.string.large_file_cleaner) -> AdAreaKeys.Interstitial.LARGE_FILE_FINISH
            context.getString(R.string.screenshot_manager) -> AdAreaKeys.Interstitial.SCREENSHOT_FINISH
            else ->  AdAreaKeys.Interstitial.FILE_MANAGE_FINISH
        }
    }

    fun canRequestAds(context: Context): Boolean = Core.canRequestAds()

    suspend fun preloadLaunchAds(context: Context): LaunchAdsPreloadResult {
        if (!canRequestAds(context)) return LaunchAdsPreloadResult.CONSENT_UNAVAILABLE
        if (!AdConfig.isOpen) return LaunchAdsPreloadResult.POLICY_DISABLED
        return try {
            AdLoader.fillAd(LogAdParam.scene_open_app)
            LaunchAdsPreloadResult.REQUESTED
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "preloadLaunchAds failed", exception)
            LaunchAdsPreloadResult.FAILED
        }
    }

    fun showLaunchOpenAd(
        activity: Activity,
        onLaunched: () -> Unit = {},
        onContinue: () -> Unit,
    ): Job = showFullScreenAd(
        activity, AdAreaKeys.Open.OPEN_PAGE, AdFormat.OPEN, ScreenAdTrigger.ENTER,
        onLaunched = onLaunched, onClosed = onContinue,
    )

    fun showInterstitial(activity: Activity, areaKey: String?, onClosed: () -> Unit) {
        if (activity.isUnavailable()) return
        if (areaKey.isNullOrBlank()) {
            onClosed()
            return
        }
        showFullScreenAd(activity, areaKey, AdFormat.INTER, ScreenAdTrigger.LEAVE, onClosed = onClosed)
    }

    private fun showFullScreenAd(
        activity: Activity,
        areaKey: String,
        format: AdFormat,
        trigger: ScreenAdTrigger,
        onLaunched: () -> Unit = {},
        onClosed: () -> Unit,
    ): Job {
        val owner = activity as? LifecycleOwner
        if (owner == null) {
            if (!activity.isUnavailable()) onClosed()
            return Job().apply { complete() }
        }
        return owner.lifecycleScope.launch(Dispatchers.Main.immediate) {
            SdkInitUtils.awaitInitialization(timeoutMs = SDK_INITIALIZATION_WAIT_TIMEOUT_MS)
            if (activity.isUnavailable()) return@launch
            if (!SdkInitUtils.isInitialized()) {
                onClosed()
                return@launch
            }
            val context = ScreenAdContext(
                areaKey = areaKey,
                adFormat = format,
                adPlatform = AdConfig.defaultPlatform,
                trigger = trigger,
            )
            var handedOff = false
            try {
                launchAdActivityRequest(
                    scope = this,
                    lifecycle = owner.lifecycle,
                    canShow = {
                        !activity.isUnavailable() &&
                            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    },
                    launchAd = { AdActivity.showAd(activity, context) },
                    onLaunched = {
                        handedOff = true
                        fullScreenRequestCount++
                        onLaunched()
                    },
                    onClosed = onClosed,
                ).join()
            } finally {
                if (handedOff) fullScreenRequestCount--
            }
        }
    }

    /** Keep the original foreground placement while using the SDK's standard ad page. */
    fun updateForegroundHostState(activity: Activity, isResumed: Boolean) {
        if (isResumed) {
            foregroundHost = WeakReference(activity)
            foregroundHostResumed = true
        } else if (foregroundHost?.get() === activity) {
            foregroundHostResumed = false
        }
        if (!processObserverRegistered) {
            processObserverRegistered = true
            ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        }
        if (isResumed && activity is LifecycleOwner) {
            activity.lifecycleScope.launch {
                activity.lifecycle.withResumed { }
                tryShowForegroundAd()
            }
        }
        tryShowForegroundAd()
    }

    fun suppressNextForegroundOpenAd() = foregroundOpportunity.suppressNextReturn()

    fun setForegroundOpenAdEnabled(enabled: Boolean) = foregroundOpportunity.setEnabled(enabled)

    private fun tryShowForegroundAd() {
        val activity = foregroundHost?.get() ?: return
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        val eligible = foregroundHostResumed && activity !is SplashActivity &&
            lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true &&
            !activity.isUnavailable() && !AppStatus.isShowingAd && fullScreenRequestCount == 0 &&
            foregroundRequest?.isActive != true
        if (!foregroundOpportunity.take(eligible)) return
        foregroundRequest = showFullScreenAd(
            activity, AdAreaKeys.Open.FOREGROUND, AdFormat.OPEN, ScreenAdTrigger.RETURN,
            onClosed = {},
        )
    }

    fun showFileAccessCancelInterstitial(activity: Activity, onClosed: () -> Unit = {}) {
        showInterstitial(activity, AdAreaKeys.Interstitial.FILE_ACCESS_CANCEL, onClosed)
    }

    fun showReturnHomeInterstitial(activity: Activity, onClosed: () -> Unit) {
        showInterstitial(activity, AdAreaKeys.Interstitial.RETURN_HOME_PAGE, onClosed)
    }


    fun showFeatureEntryInterstitial(
        activity: Activity,
        nextTag: String,
        onClosed: () -> Unit,
    ) {
        showFeatureEntryInterstitialByTag(activity, nextTag, onClosed)
    }

    fun showFeatureEntryInterstitialByTag(
        activity: Activity,
        nextTag: String,
        onClosed: () -> Unit,
    ) {
        val areaKey = featureEntryInterstitialAreaKey(activity, nextTag)
        Log.i(TAG, "feature entry tag resolved: tag=$nextTag, areaKey=$areaKey")
        showInterstitial(activity, areaKey, onClosed)
    }

    fun showFeatureEntryInterstitialByAreaKey(
        activity: Activity,
        areaKey: String,
        onClosed: () -> Unit,
    ) {
        showInterstitial(activity, InterstitialAreaResolver.forwardAreaKey(areaKey), onClosed)
    }

    fun showUninstallFirstInterstitial(activity: Activity, onClosed: () -> Unit) {
        showInterstitial(activity, AdAreaKeys.Interstitial.UNINSTALL_ONE, onClosed)
    }

    fun showUninstallSecondInterstitial(activity: Activity, onClosed: () -> Unit) {
        showInterstitial(activity, AdAreaKeys.Interstitial.UNINSTALL_TWO, onClosed)
    }

    private fun featureEntryInterstitialAreaKey(context: Context, nextTag: String): String =
        InterstitialAreaResolver.featureEntry(
            labels = FeatureEntryLabels(
                removeJunk = context.getString(R.string.remove_junk),
                notificationBar = context.getString(R.string.notification_bar),
                appUsage = context.getString(R.string.app_usage),
                networkScan = context.getString(R.string.network_scan),
                networkSpeed = context.getString(R.string.network_speed),
                networkUsage = context.getString(R.string.network_usage),
                processManager = context.getString(R.string.process_manager),
                appManager = context.getString(R.string.app_manager),
                batteryInfo = context.getString(R.string.battery_info),
                speakerCleaner = context.getString(R.string.speaker_cleaner),
                images = context.getString(R.string.images),
                videos = context.getString(R.string.video),
                audio = context.getString(R.string.audio),
                apk = context.getString(R.string.apk),
                largeFiles = context.getString(R.string.large_files),
                screenshots = context.getString(R.string.screenshots),
            ),
            nextTag = nextTag,
        )




    private fun Activity.isUnavailable(): Boolean = isFinishing || isDestroyed
}

/** A request belongs to this host instance and completes only after its ad page returns. */
internal fun launchAdActivityRequest(
    scope: CoroutineScope,
    lifecycle: Lifecycle,
    canShow: () -> Boolean,
    launchAd: () -> Boolean,
    onLaunched: () -> Unit = {},
    onClosed: () -> Unit,
): Job = scope.launch(Dispatchers.Main.immediate) {
    val returned = CompletableDeferred<Unit>()
    var paused = false
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE -> paused = true
            Lifecycle.Event.ON_RESUME -> if (paused) returned.complete(Unit)
            Lifecycle.Event.ON_DESTROY -> returned.cancel()
            else -> Unit
        }
    }
    try {
        currentCoroutineContext().ensureActive()
        if (lifecycle.currentState == Lifecycle.State.DESTROYED) return@launch
        if (canShow()) {
            lifecycle.addObserver(observer)
            if (launchAd()) {
                onLaunched()
                returned.await()
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        // A launch failure completes this request so the original business action can continue.
    } finally {
        lifecycle.removeObserver(observer)
    }
    currentCoroutineContext().ensureActive()
    onClosed()
}

/** A real return creates one opportunity; cold starts and ad-owned pauses do not. */
internal class ForegroundAdOpportunity {
    private var enabled = true
    private var backgrounded = false
    private var suppressNext = false
    private var pending = false

    fun onBackground(isShowingAd: Boolean) {
        backgrounded = !isShowingAd
    }

    fun onForeground(isShowingAd: Boolean) {
        if (!backgrounded) return
        backgrounded = false
        pending = enabled && !isShowingAd && !suppressNext
        suppressNext = false
    }

    fun suppressNextReturn() {
        suppressNext = true
        pending = false
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) pending = false
    }

    fun take(eligible: Boolean): Boolean {
        if (!enabled || !pending || !eligible) return false
        pending = false
        return true
    }
}
