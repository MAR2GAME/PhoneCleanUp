package com.cleanphone.freespace.utility.advertise

import com.cleanphone.freespace.utility.base.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvertiseConfigFactoryTest {
    @Test
    fun createUsesPhoneCleanUpIdentityAndDisablesUnavailableInfrastructure() {
        val config = AdvertiseConfigFactory.create(
            packageName = "com.cleanphone.freespace.utility",
            isDebug = true,
        )

        assertEquals("com.cleanphone.freespace.utility", config.packageName)
        assertEquals(AppConfig.PRIVACY_URL, config.privacyUrl)
        assertEquals(AppConfig.TERMS_URL, config.termsUrl)
        assertEquals(AdvertiseRuntimeCapabilities.SERVER_ENABLED, config.server.enabled)
        assertEquals(AdvertiseRuntimeCapabilities.FIREBASE_ANALYTICS_ENABLED, config.firebase.analyticsEnabled)
        assertEquals(AdvertiseRuntimeCapabilities.FIREBASE_MESSAGING_ENABLED, config.firebase.messagingEnabled)
        assertEquals(AdvertiseRuntimeCapabilities.REMOTE_CONFIG_ENABLED, config.remoteConfig.enabled)
        assertEquals(AdvertiseRuntimeCapabilities.THINKING_ENABLED, config.thinking.enabled)
        assertEquals(AdvertiseRuntimeCapabilities.SINGULAR_ENABLED, config.singular.enabled)
        assertEquals(false, config.tiktok.enabled)
        assertEquals(false, config.safe.enabled)
        assertEquals(AdvertiseRuntimeCapabilities.PUSH_ENABLED, config.push.enabled)
        assertEquals(AdvertiseRuntimeCapabilities.NOTIFICATIONS_ENABLED, config.notifications.enabled)
        assertEquals(AdvertiseRuntimeCapabilities.PLAY_INTEGRITY_ENABLED, config.playIntegrity.enabled)
    }
}
