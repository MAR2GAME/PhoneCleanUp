package com.cleanphone.freespace.utility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertiseSdkSyncTest {
    @Test
    fun advertiseDependencyConfigurationAndInitializationAreWired() {
        val settings = File("../settings.gradle.kts").readText()
        val appGradle = File("build.gradle.kts").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val myApp = File(
            "src/main/java/com/cleanphone/freespace/utility/base/MyApp.kt",
        ).readText()

        assertTrue(settings.contains("http://192.168.10.128:8081/repository/maven-public/"))
        assertTrue(settings.contains("https://maven.singular.net/"))
        assertTrue(settings.contains("https://cboost.jfrog.io/artifactory/chartboost-ads/"))
        assertTrue(settings.contains("https://android-sdk.is.com/"))
        assertTrue(
            settings.contains(
                "https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea",
            ),
        )
        assertTrue(settings.contains("https://artifact.bytedance.com/repository/pangle"))

        assertTrue(appGradle.contains("implementation(\"com.pdffox:advertise:1.0."))
        assertTrue(appGradle.contains("ADV_ADMOB_APP_ID"))
        assertTrue(appGradle.contains("ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID"))
        assertTrue(appGradle.contains("buildConfig = true"))

        assertTrue(manifest.contains("com.google.android.gms.ads.APPLICATION_ID"))
        assertTrue(manifest.contains("${'$'}{advAdmobAppId}"))

        val configFactory = File(
            "src/main/java/com/cleanphone/freespace/utility/advertise/AdvertiseConfigFactory.kt",
        )
        assertTrue(configFactory.isFile)
        assertTrue(
            File(
                "src/main/java/com/cleanphone/freespace/utility/advertise/AdvertiseRuntimeCapabilities.kt",
            ).isFile,
        )
        assertTrue(
            File(
                "src/main/java/com/cleanphone/freespace/utility/utils/SdkInitUtils.kt",
            ).isFile,
        )
        assertTrue(File("src/main/res/raw/ad_policy.json").isFile)
        assertTrue(File("src/main/res/raw/adload_config.json").isFile)
        assertTrue(myApp.contains("SdkInitUtils.initSDKAsync(this)"))
        assertTrue(myApp.contains("SdkInitUtils.cleanup()"))

        assertFalse(
            appGradle.contains(
                "advStringProperty(\"ADV_ADMOB_APP_ID\", fallback = \"ca-app-pub-",
            ),
        )
        assertFalse(
            appGradle.contains(
                "advStringProperty(\"ADV_FACEBOOK_CLIENT_TOKEN\", fallback =",
            ),
        )
        assertFalse(
            appGradle.contains(
                "advStringProperty(\"ADV_REMOTE_CONFIG_ENCRYPTION_KEY\", fallback =",
            ),
        )
    }
}
