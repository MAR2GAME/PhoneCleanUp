package com.cleanphone.freespace.utility.utils

import java.text.DateFormat
import java.util.Date
import java.util.Locale

object NetworkScanFormatter {
    private const val UNKNOWN_SSID = "<unknown ssid>"

    fun cleanSsid(rawSsid: String?): String {
        if (rawSsid.isNullOrBlank()) return UNKNOWN_SSID
        val trimmed = rawSsid.trim().removePrefix("\"").removeSuffix("\"")
        if (trimmed.isBlank() || isUnknownSsid(trimmed)) {
            return UNKNOWN_SSID
        }
        return trimmed
    }

    fun isUnknownSsid(ssid: String): Boolean = ssid.equals(UNKNOWN_SSID, ignoreCase = true)

    fun resolveSecurityLabel(capabilities: String?): String {
        if (capabilities.isNullOrBlank()) return "Unknown"
        val upper = capabilities.uppercase(Locale.US)
        return when {
            "WPA3" in upper || "SAE" in upper -> "WPA3"
            "WPA2" in upper -> "WPA2"
            "WPA" in upper -> "WPA/WPA2"
            "WEP" in upper -> "WEP"
            "OWE" in upper -> "OWE"
            "ESS" in upper -> "Open"
            else -> "Unknown"
        }
    }

    fun formatLastScanTime(timestamp: Long, locale: Locale): String {
        if (timestamp <= 0L) return "-"
        return DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
            locale
        ).format(Date(timestamp))
    }
}
