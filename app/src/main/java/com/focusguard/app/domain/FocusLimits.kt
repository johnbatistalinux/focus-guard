package com.focusguard.app.domain

/**
 * Single source of truth for parameter ranges, coercion rules, quota level calculations,
 * and anchor habit sanitization across FocusGuard.
 */
object FocusLimits {
    val PAUSE_SECONDS: IntRange = 3..120
    val SESSION_MINUTES: IntRange = 1..60
    val COOLDOWN_MINUTES: IntRange = 1..180
    val DAILY_QUOTA_MINUTES: IntRange = 5..240
    val MINUTE_OF_DAY_RANGE: IntRange = 0..1439

    const val MAX_ANCHOR_HABIT_LENGTH: Int = 40
    const val TAPERING_DURATION_DAYS: Int = 21
    const val DEFAULT_QUIET_START_MINUTE: Int = 22 * 60 + 30 // 22:30
    const val DEFAULT_QUIET_END_MINUTE: Int = 7 * 60 + 30    // 07:30

    val COMMITMENT_LOCK_DAY_OPTIONS: List<Int> = listOf(3, 7, 14, 30)
    val QUIET_START_PRESETS: List<Int> = listOf(21 * 60, 22 * 60, 22 * 60 + 30, 23 * 60, 0)
    val QUIET_END_PRESETS: List<Int> = listOf(6 * 60, 7 * 60, 7 * 60 + 30, 8 * 60, 9 * 60)

    enum class ChangedField {
        SESSION_DURATION,
        DAILY_QUOTA
    }

    enum class QuotaLevel {
        OK,
        WARNING,
        EXHAUSTED
    }

    /**
     * Sanitizes custom anchor habit text: removes line breaks, trims whitespace,
     * truncates to [MAX_ANCHOR_HABIT_LENGTH], and falls back to default if blank.
     */
    fun sanitizeAnchorHabit(raw: String?): String {
        if (raw.isNullOrBlank()) return FocusConfig.DEFAULT_ANCHOR_HABIT
        val cleaned = raw.replace("\n", " ").replace("\r", " ").trim()
        val truncated = if (cleaned.length > MAX_ANCHOR_HABIT_LENGTH) {
            cleaned.substring(0, MAX_ANCHOR_HABIT_LENGTH).trim()
        } else {
            cleaned
        }
        return truncated.ifBlank { FocusConfig.DEFAULT_ANCHOR_HABIT }
    }

    /**
     * Coerces session duration and daily quota ensuring invariant: sessionMinutes <= dailyQuotaMinutes.
     * When [changed] is SESSION_DURATION, expanding session beyond quota lifts daily quota.
     * When [changed] is DAILY_QUOTA, shrinking quota below session pulls session duration down.
     */
    fun coerceSessionAndQuota(
        sessionMinutes: Int,
        dailyQuotaMinutes: Int,
        changed: ChangedField
    ): Pair<Int, Int> {
        val clampedSession = sessionMinutes.coerceIn(SESSION_MINUTES)
        val clampedQuota = dailyQuotaMinutes.coerceIn(DAILY_QUOTA_MINUTES)

        return when (changed) {
            ChangedField.SESSION_DURATION -> {
                val effectiveQuota = maxOf(clampedQuota, clampedSession)
                Pair(clampedSession, effectiveQuota)
            }
            ChangedField.DAILY_QUOTA -> {
                val effectiveSession = minOf(clampedSession, clampedQuota)
                Pair(effectiveSession, clampedQuota)
            }
        }
    }

    /**
     * Calculates the quota risk level based on consumed and total minutes.
     * - OK: < 70% consumed
     * - WARNING: >= 70% and < 100% consumed
     * - EXHAUSTED: >= 100% consumed
     */
    fun quotaLevel(usedMinutes: Long, quotaMinutes: Int): QuotaLevel {
        if (quotaMinutes <= 0 || usedMinutes >= quotaMinutes) {
            return QuotaLevel.EXHAUSTED
        }
        val ratio = usedMinutes.toDouble() / quotaMinutes.toDouble()
        return when {
            ratio >= 1.0 -> QuotaLevel.EXHAUSTED
            ratio >= 0.70 -> QuotaLevel.WARNING
            else -> QuotaLevel.OK
        }
    }

    /**
     * Finds a predefined [FocusPreset] matching all 4 timing parameters, or null if custom.
     */
    fun findMatchingPreset(
        pauseSeconds: Int,
        sessionMinutes: Int,
        cooldownMinutes: Int,
        dailyQuotaMinutes: Int
    ): FocusPreset? {
        return FocusPreset.entries.firstOrNull { preset ->
            preset.matches(pauseSeconds, sessionMinutes, cooldownMinutes, dailyQuotaMinutes)
        }
    }

    /**
     * Computes sorted, distinct session budget choices (in minutes) from `[1, 3, maxSessionMinutes]`
     * that fit within [remainingQuotaSeconds]. If remaining quota is between 1 and 59 seconds,
     * returns `listOf(1)` so the user can still spend the final partial minute.
     */
    fun computeSessionChoices(maxSessionMinutes: Int, remainingQuotaSeconds: Long): List<Int> {
        if (remainingQuotaSeconds <= 0L) return emptyList()
        val clampedMax = maxSessionMinutes.coerceIn(SESSION_MINUTES)
        val remainingWholeMinutes = (remainingQuotaSeconds / 60L).toInt()
        if (remainingWholeMinutes <= 0) {
            return listOf(1)
        }
        val effectiveMax = minOf(clampedMax, remainingWholeMinutes)
        return listOf(1, 3, effectiveMax)
            .filter { it in 1..effectiveMax }
            .distinct()
            .sorted()
    }

    /**
     * Formats a minute-of-day (`0..1439`) as `"HH:mm"`.
     */
    fun formatMinuteOfDay(minuteOfDay: Int): String {
        val safe = minuteOfDay.coerceIn(MINUTE_OF_DAY_RANGE)
        val hours = safe / 60
        val mins = safe % 60
        return "${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}"
    }
}
