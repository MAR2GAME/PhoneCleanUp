package com.cleanphone.freespace.utility.advertise

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AdvertiseConfigFactoryTest {
    @Test
    fun migratedPolicyPreservesAllExistingPlacementsAndCaps() {
        val config = JsonParser.parseString(File("src/main/res/raw/ad_config.json").readText()).asJsonObject
        val units = config["ad_units"].asJsonObject
        val openKeys = AdAreaKeys.Open::class.java.declaredFields
            .filter { it.type == String::class.java }.map { it.get(null) as String }.toSet()
        val interKeys = AdAreaKeys.Interstitial::class.java.declaredFields
            .filter { it.type == String::class.java }.map { it.get(null) as String }.toSet()
        assertEquals(openKeys + interKeys + "openMainBottomAdv", units.keySet())
        for (key in openKeys + interKeys) {
            val unit = units[key].asJsonObject
            assertEquals(1.0, unit["rate"].asDouble, 0.0)
            assertEquals(if (key in openKeys) "OPEN" else "INTER", unit["format"].asString)
            assertEquals(if (key in openKeys) 4 else 5, unit["1HMax"].asInt)
            assertEquals(if (key in openKeys) 20 else 10, unit["24HMax"].asInt)
            assertEquals(if (key == AdAreaKeys.Open.FOREGROUND) 30 else 0, unit["interval"].asInt)
        }
        assertEquals(0.0, units["openMainBottomAdv"].asJsonObject["rate"].asDouble, 0.0)
        assertEquals("ADMOB", config["defaultPlatform"].asString)
        assertFalse(config["autoRate"].asJsonObject["enable"].asBoolean)
    }
}
