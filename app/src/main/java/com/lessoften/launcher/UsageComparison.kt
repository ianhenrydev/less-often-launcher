package com.lessoften.launcher

/** Checkpoints must already represent complete coverage through the same local five-minute cutoff. */
internal object UsageComparison {
    fun isNormalDay(date: java.time.LocalDate, zone: java.time.ZoneId): Boolean =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - date.atStartOfDay(zone).toInstant().toEpochMilli() == 86_400_000L
    data class Checkpoint(val millis: Long, val complete: Boolean)
    data class Result(val baselineDays: Int, val differenceMillis: Long, val cutoffMinute: Int) {
        fun label(): String {
            val absolute = kotlin.math.abs(differenceMillis)
            val minutes = (absolute + 30_000) / 60_000
            return when {
                absolute < 60_000 -> "About usual by now"
                differenceMillis < 0 -> "${minutes}m less than usual by now"
                else -> "${minutes}m more than usual by now"
            }
        }
    }
    fun compare(currentMinute: Int, today: Map<Int, Checkpoint>, previous: List<Map<Int, Checkpoint>>): Result? {
        for (cutoff in currentMinute.coerceIn(0, 1439) / 5 * 5 downTo 5 step 5) {
            val current = today[cutoff]?.takeIf { it.complete } ?: continue
            val eligible = previous.take(7).mapNotNull { it[cutoff]?.takeIf(Checkpoint::complete)?.millis }
            if (eligible.size >= 3) return Result(eligible.size, current.millis - eligible.sum() / eligible.size, cutoff)
        }
        return null
    }
}
