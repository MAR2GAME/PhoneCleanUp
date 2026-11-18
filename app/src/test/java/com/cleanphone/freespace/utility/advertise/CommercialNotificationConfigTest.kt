package com.cleanphone.freespace.utility.advertise

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommercialNotificationConfigTest {
    private val deletionRoutes = mapOf(
        "delete_photos" to "screenshots",
        "delete_videos" to "large_files",
        "delete_files" to "large_files",
    )

    @Test
    fun notificationContentPreservesAllMessagesTranslationsAndDestinations() {
        val contents = readConfig("notification_content.json")["contents"].asJsonArray
            .map { it.asJsonObject }
        assertEquals(55, contents.size)
        assertEquals(388, contents.sumOf { it["Languages"].asJsonObject.size() })
        assertEquals(
            mapOf("es" to 55, "fr" to 55, "de" to 55, "ja" to 55, "ko" to 55,
                "zh" to 55, "zh-TW" to 55, "en" to 3),
            contents.flatMap { it["Languages"].asJsonObject.keySet() }.groupingBy { it }.eachCount(),
        )
        contents.forEach { content ->
            assertTrue(content["Languages"].isJsonObject)
            assertTrue(content["Title"].asString.isNotBlank())
            assertTrue(content["Scenes"].asJsonArray.size() > 0)
            content["Languages"].asJsonObject.entrySet().forEach { (_, translation) ->
                assertTrue(translation.isJsonObject)
                val fields = translation.asJsonObject
                assertTrue(fields["title"].isJsonPrimitive)
                assertTrue(fields["content"].isJsonPrimitive)
                assertTrue(fields["button"].isJsonPrimitive)
            }
        }

        val originalMessages = contents.filter { content ->
            content["Scenes"].asJsonArray.none { it.asString in deletionRoutes }
        }
        assertEquals(52, originalMessages.size)
        assertEquals(
            mapOf("app_manager" to 6, "process_manager" to 6, "battery_info" to 7,
                "screenshots" to 8, "large_files" to 8, "junk_files" to 9,
                "speaker_cleaner" to 7, "home" to 1),
            originalMessages.groupingBy { it["Route"].asString }.eachCount(),
        )
    }

    @Test
    fun deletionScenesSelectTheirOwnOriginalMessageAndRoute() {
        val contents = readConfig("notification_content.json")["contents"].asJsonArray
            .map { it.asJsonObject }
        deletionRoutes.forEach { (scene, route) ->
            // Match the SDK selector: an explicit scene or a wildcard makes content eligible.
            val eligible = contents.filter { content ->
                content["Scenes"].asJsonArray.any { it.asString == scene || it.asString == "*" }
            }
            assertEquals("Only the original deletion message should match $scene", 1, eligible.size)
            assertEquals(route, eligible.single()["Route"].asString)
            assertEquals(8, eligible.single()["Languages"].asJsonObject.size())
        }
        assertFalse(contents.any { it["Route"].asString.startsWith("/recover") })
    }

    @Test
    fun deletionPolicyPreservesTimingWithoutEnablingSdkSampleTriggers() {
        val config = readConfig("notification_config.json")
        val triggers = config["triggers"].asJsonObject
        assertTrue(config["isSend"].asBoolean)
        assertEquals(deletionRoutes.keys, triggers.keySet())
        deletionRoutes.keys.forEach { scene ->
            val trigger = triggers[scene].asJsonObject
            assertEquals(600, trigger["first_delay"].asInt)
            assertEquals(3600, trigger["interval_batch"].asInt)
            assertEquals(1, trigger["count"].asInt)
        }
        assertEquals(0, config["timer"].asJsonObject.size())
        assertEquals(0, config["groups"].asJsonObject["list"].asJsonArray.size())
    }

    private fun readConfig(name: String): JsonObject = JsonParser.parseString(
        File("src/main/res/raw/$name").readText(Charsets.UTF_8),
    ).asJsonObject
}
