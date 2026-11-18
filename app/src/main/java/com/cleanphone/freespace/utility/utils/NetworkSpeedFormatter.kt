package com.cleanphone.freespace.utility.utils

import java.util.Locale

object NetworkSpeedFormatter {
    fun formatSpeedMbps(value: Double?, locale: Locale): String {
        if (value == null || value <= 0.0) return "--"
        return String.format(locale, "%.1f", value)
    }
}
