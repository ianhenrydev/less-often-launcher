package com.lessoften.launcher

import java.time.LocalDate

internal object Retention {
    fun expired(key: String, prefix: String, today: LocalDate): Boolean {
        val date = runCatching { LocalDate.parse(key.removePrefix(prefix).substringBefore('|')) }.getOrNull()
        return date == null || date.isBefore(today.minusDays(7))
    }
}
