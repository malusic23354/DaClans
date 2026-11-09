package net.malusic.daclans.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormat {

    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    fun date(epochMillis: Long): String = date.format(Instant.ofEpochMilli(epochMillis))

    fun ago(epochMillis: Long): String {
        if (epochMillis <= 0) return "unknown"
        val diff = System.currentTimeMillis() - epochMillis
        if (diff < 60_000) return "just now"
        val minutes = diff / 60_000
        if (minutes < 60) return "${minutes}m ago"
        val hours = minutes / 60
        if (hours < 24) return "${hours}h ago"
        val days = hours / 24
        return "${days}d ago"
    }
}
