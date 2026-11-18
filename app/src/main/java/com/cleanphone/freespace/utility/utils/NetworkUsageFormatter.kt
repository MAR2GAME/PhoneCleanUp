package com.cleanphone.freespace.utility.utils

import java.util.Locale

object NetworkUsageFormatter {
    fun formatBytes(bytes: Long, locale: Locale): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(locale, "%.1f GB", bytes / gb)
            bytes >= mb -> String.format(locale, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(locale, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
