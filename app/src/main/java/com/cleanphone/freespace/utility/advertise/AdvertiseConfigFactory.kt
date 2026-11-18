package com.cleanphone.freespace.utility.advertise

import android.content.Context
import com.cleanphone.freespace.utility.BuildConfig
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.pdffox.adv.AdvertiseSdkConfig
import com.pdffox.adv.AdvertiseSdkConfigBuilder
import com.pdffox.adv.AdvertiseSdkConfigs
import com.pdffox.adv.NotificationActionConfig
import com.pdffox.adv.NotificationFeatureConfig
import com.pdffox.adv.NotificationRouteMapping
import com.pdffox.adv.PushSceneKeyConfig

object AdvertiseConfigFactory {
    private const val DEFAULT_PERSISTENT_CONTENT_TEXT = "PhoneCleanUp"

    fun create(context: Context): AdvertiseSdkConfig {
        return AdvertiseSdkConfigs.create(context, BuildConfig.DEBUG) {
            configureCoolCleaner(
                packageName = context.packageName,
                persistentContentText = context.getString(R.string.app_name),
                isDebug = BuildConfig.DEBUG,
                applicationContext = context,
            )
        }
    }

    fun create(packageName: String, isDebug: Boolean): AdvertiseSdkConfig {
        return AdvertiseSdkConfigs.create(packageName, isDebug) {
            configureCoolCleaner(
                packageName = packageName,
                persistentContentText = DEFAULT_PERSISTENT_CONTENT_TEXT,
                isDebug = BuildConfig.DEBUG,
                applicationContext = null,
            )
        }
    }

    private fun AdvertiseSdkConfigBuilder.configureCoolCleaner(
        packageName: String,
        persistentContentText: String,
        isDebug: Boolean,
        applicationContext: Context?,
    ) {
        legal(
            privacyUrl = AppConfig.PRIVACY_URL,
            termsUrl = AppConfig.TERMS_URL,
        )
        defaultTopic(BuildConfig.ADV_DEFAULT_TOPIC)
        resources(
            adPolicyRawResId = R.raw.ad_policy,
            adLoadConfigRawResId = R.raw.adload_config,
            cloudCidrsRawResId = com.pdffox.adv.R.raw.cloud,
            googleCidrsRawResId = com.pdffox.adv.R.raw.google,
            pushConfigRawResId = R.raw.push,
        )
        server(
            enabled = AdvertiseRuntimeCapabilities.SERVER_ENABLED,
            releaseHost = AppConfig.ADVHost,
            testHost = "",
            parseTokenKey = "TianWangGaiDiHu",
        )
        firebase(
            analyticsEnabled = AdvertiseRuntimeCapabilities.FIREBASE_ANALYTICS_ENABLED,
            messagingEnabled = AdvertiseRuntimeCapabilities.FIREBASE_MESSAGING_ENABLED,
            subscribeDefaultTopic = AdvertiseRuntimeCapabilities.FIREBASE_SUBSCRIBE_DEFAULT_TOPIC,
        )
        remoteConfig(
            enabled = AdvertiseRuntimeCapabilities.REMOTE_CONFIG_ENABLED,
            encryptionKeyBase64 = BuildConfig.ADV_REMOTE_CONFIG_ENCRYPTION_KEY,
            encryptionKeyId = BuildConfig.ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID,
        )
        thinking(
            enabled = AdvertiseRuntimeCapabilities.THINKING_ENABLED,
            appKey = AppConfig.THINKING_APPID,
            serverUrl = AppConfig.THINKING_URL,
        )
        singular(
            enabled = AdvertiseRuntimeCapabilities.SINGULAR_ENABLED,
            apiKey = AppConfig.Singular_Api_Key,
            secret = AppConfig.Singular_Secret,
        )
        adMob(
            enabled = AdvertiseRuntimeCapabilities.ADMOB_ENABLED,
            appId = BuildConfig.ADV_ADMOB_APP_ID,
            bannerId = BuildConfig.ADV_ADMOB_BANNER_ID,
            interstitialId = BuildConfig.ADV_ADMOB_INTERSTITIAL_ID,
            nativeId = BuildConfig.ADV_ADMOB_NATIVE_ID,
            openId = BuildConfig.ADV_ADMOB_OPEN_ID,
            rewardedId = BuildConfig.ADV_ADMOB_REWARDED_ID,
        )
        facebook(
            enabled =false,
            appId = "",
            clientToken = "",
        )
        tiktok(
            enabled =false,
            accessToken = "",
            ttAppId = "",
            appId = packageName,
        )
        safe(
            enabled = false,
            expectedSignatures = "",
            expectedPackageName = packageName,
            rejectDebuggableBuilds =false,
            rejectDebuggerAttached =false,
            killProcessOnFailure =false,
        )
        push(
            enabled = AdvertiseRuntimeCapabilities.PUSH_ENABLED,
            persistentServiceEnabled = AdvertiseRuntimeCapabilities.PUSH_PERSISTENT_SERVICE_ENABLED,
            firebaseMessagingServiceEnabled = AdvertiseRuntimeCapabilities.PUSH_FIREBASE_MESSAGING_SERVICE_ENABLED,
            serviceStarterJobEnabled = AdvertiseRuntimeCapabilities.PUSH_SERVICE_STARTER_JOB_ENABLED,
            bootReceiverEnabled = AdvertiseRuntimeCapabilities.PUSH_BOOT_RECEIVER_ENABLED,
            notificationDeletedReceiverEnabled = AdvertiseRuntimeCapabilities.PUSH_NOTIFICATION_DELETED_RECEIVER_ENABLED,
            fileProviderEnabled = AdvertiseRuntimeCapabilities.PUSH_FILE_PROVIDER_ENABLED,
            deletionObserverEnabled = AdvertiseRuntimeCapabilities.PUSH_DELETION_OBSERVER_ENABLED,
            sceneKeys = PushSceneKeyConfig(
                imageDeleted = "delete_photos",
                videoDeleted = "delete_videos",
                fileDeleted = "delete_files",
            ),
        )
        notifications(notificationConfig(persistentContentText, applicationContext))
        playIntegrity(
            enabled = true,
            cloudProjectNumber = 449354747802L,
        )

    }

    private fun notificationConfig(
        persistentContentText: String,
        applicationContext: Context?,
    ): NotificationFeatureConfig {
        fun label(resourceId: Int, fallback: String): String =
            applicationContext?.getString(resourceId) ?: fallback

        return NotificationFeatureConfig(
            enabled = AdvertiseRuntimeCapabilities.NOTIFICATIONS_ENABLED,
            smallIconResId = R.mipmap.ic_launcher,
            persistentContentText = persistentContentText,
            persistentActions = listOf(
                NotificationActionConfig("junk_files", "Clean", R.mipmap.ic_removejunk),
                NotificationActionConfig("large_files", "File", R.mipmap.ic_file_push),
                NotificationActionConfig("app_manager", "App", R.mipmap.ic_app),
                NotificationActionConfig("battery_info", "Battery", R.mipmap.ic_battery),
            ),
            routeMappings = notificationRouteMappings(),
        )
    }

    internal fun notificationRouteMappings(): List<NotificationRouteMapping> {
        return listOf(
            route("home", "home", R.mipmap.ic_launcher),
            route("main", "main", R.mipmap.ic_launcher),
            route("/main", "main", R.mipmap.ic_launcher),
            route("notification_bar", "notification_bar", R.mipmap.ic_nt_push),
            route("junk_files", "junk_files", R.mipmap.ic_removejunk),
            route("clean", "clean", R.mipmap.ic_removejunk),
            route("/scan", "scan", R.mipmap.ic_removejunk),
            route("process_manager", "process_manager", R.mipmap.ic_process_manager),
            route("/processManager", "process_manager", R.mipmap.ic_process_manager),
            route("app_manager", "app_manager", R.mipmap.ic_appmanager),
            route("/appManager", "app_manager", R.mipmap.ic_appmanager),
            route("battery_info", "battery_info", R.mipmap.ic_battery),
            route("/batteryInfo", "battery_info", R.mipmap.ic_battery),
            route("speaker_cleaner", "speaker_cleaner", R.mipmap.ic_speaker),
            route("/speakerCleaner", "speaker_cleaner", R.mipmap.ic_speaker),
            route("large_files", "large_files", R.mipmap.ic_file),
            route("/largeFileManager", "large_files", R.mipmap.ic_file),
            route("screenshots", "screenshots", R.mipmap.ic_shotes),
            route("screenshot_manager", "screenshots", R.mipmap.ic_shotes),
            route("/screenshotManager", "screenshots", R.mipmap.ic_shotes),
            route("network_scan", "networkscan", R.mipmap.ic_network_scan),
            route("network_speed", "networkspeed", R.mipmap.ic_network_scan),
            route("network_usage", "networkusage", R.mipmap.ic_neteork_usage),
            route("app_usage", "appusage", R.mipmap.ic_app)
        )
    }

    private fun route(rawRoute: String, route: String, iconResId: Int): NotificationRouteMapping {
        return NotificationRouteMapping(
            rawRoute = rawRoute,
            route = route,
            temporaryIconResId = iconResId,
            persistentIconResId = iconResId,
        )
    }
}
