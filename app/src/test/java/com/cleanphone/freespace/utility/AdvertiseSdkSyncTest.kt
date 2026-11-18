package com.cleanphone.freespace.utility

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AdvertiseSdkSyncTest {
    private fun config(name: String): JsonObject =
        JsonParser.parseString(File("src/main/res/raw/$name.json").readText()).asJsonObject

    @Test
    fun runtimeUsesPhoneCleanUpIdentityAndAnalytics() {
        val common = config("common_config")
        assertEquals("com.cleanphone.freespace.utility", common["package"].asString)
        assertEquals(449354747802L, common["PlayIntegrityID"].asLong)
        assertTrue(common["serverUrl"].asString.startsWith("https://"))
        // Backend identity is temporarily shared with the reference app, as requested.
        if (common["isAutoLogin"].asBoolean) {
            assertTrue(common["serverAppID"].asInt > 0)
            assertTrue(common["serverClientKey"].asString.isNotBlank())
        }
        assertEquals("140a67622f9641948b96dfa33df0f0ba", config("thinking_config")["key"].asString)
        assertEquals("https://mar2.top", config("thinking_config")["url"].asString)
        for (channel in listOf("fbEvents", "thEvents", "localEvents")) {
            assertTrue(config("log_config")[channel].asJsonArray.any { it.asString == "*" })
        }
    }

    @Test
    fun productionAdMobIdsArePreservedAndUnusedFormatsAreEmpty() {
        val admob = config("admob_config")
        assertEquals("ca-app-pub-3106003114382927/1408775389", admob["openConfig"].asJsonObject["id"].asString)
        assertEquals("ca-app-pub-3106003114382927/8987616577", admob["interConfig"].asJsonObject["id"].asString)
        assertEquals("", admob["videoConfig"].asJsonObject["id"].asString)
        assertEquals("", admob["bannerConfig"].asJsonObject["id"].asString)
        assertEquals(0, admob["nativeConfig"].asJsonObject["ads"].asJsonArray.size())
    }
}
