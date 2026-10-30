package com.focusguard.app.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusLimitsTest {

    @Test
    fun `boundary constants match expected system ranges`() {
        assertEquals(3..120, FocusLimits.PAUSE_SECONDS)
        assertEquals(1..60, FocusLimits.SESSION_MINUTES)
        assertEquals(1..180, FocusLimits.COOLDOWN_MINUTES)
        assertEquals(5..240, FocusLimits.DAILY_QUOTA_MINUTES)
        assertEquals(40, FocusLimits.MAX_ANCHOR_HABIT_LENGTH)
    }

    @Test
    fun `coerceSessionAndQuota clamps values within allowed ranges`() {
        val (sessionLow, quotaLow) = FocusLimits.coerceSessionAndQuota(
            sessionMinutes = 0,
            dailyQuotaMinutes = 1,
            changed = FocusLimits.ChangedField.SESSION_DURATION
        )
        assertEquals(1, sessionLow)
        assertEquals(5, quotaLow)

        val (sessionHigh, quotaHigh) = FocusLimits.coerceSessionAndQuota(
            sessionMinutes = 100,
            dailyQuotaMinutes = 300,
            changed = FocusLimits.ChangedField.DAILY_QUOTA
        )
        assertEquals(60, sessionHigh)
        assertEquals(240, quotaHigh)
    }

    @Test
    fun `coerceSessionAndQuota lifts quota when session exceeds it`() {
        // Changing session to 15 when quota was 10 must lift quota to 15
        val (session, quota) = FocusLimits.coerceSessionAndQuota(
            sessionMinutes = 15,
            dailyQuotaMinutes = 10,
            changed = FocusLimits.ChangedField.SESSION_DURATION
        )
        assertEquals(15, session)
        assertEquals(15, quota)

        // Changing session to 8 when quota was 20 leaves quota at 20
        val (session2, quota2) = FocusLimits.coerceSessionAndQuota(
            sessionMinutes = 8,
            dailyQuotaMinutes = 20,
            changed = FocusLimits.ChangedField.SESSION_DURATION
        )
        assertEquals(8, session2)
        assertEquals(20, quota2)
    }

    @Test
    fun `coerceSessionAndQuota pulls session down when quota shrinks below it`() {
        // Changing quota to 10 when session was 15 must pull session down to 10
        val (session, quota) = FocusLimits.coerceSessionAndQuota(
            sessionMinutes = 15,
            dailyQuotaMinutes = 10,
            changed = FocusLimits.ChangedField.DAILY_QUOTA
        )
        assertEquals(10, session)
        assertEquals(10, quota)

        // Changing quota to 25 when session was 10 leaves session at 10
        val (session2, quota2) = FocusLimits.coerceSessionAndQuota(
            sessionMinutes = 10,
            dailyQuotaMinutes = 25,
            changed = FocusLimits.ChangedField.DAILY_QUOTA
        )
        assertEquals(10, session2)
        assertEquals(25, quota2)
    }

    @Test
    fun `quotaLevel calculates levels according to thresholds`() {
        // < 70% is OK
        assertEquals(FocusLimits.QuotaLevel.OK, FocusLimits.quotaLevel(usedMinutes = 0, quotaMinutes = 30))
        assertEquals(FocusLimits.QuotaLevel.OK, FocusLimits.quotaLevel(usedMinutes = 20, quotaMinutes = 30)) // 66.6%

        // >= 70% and < 100% is WARNING
        assertEquals(FocusLimits.QuotaLevel.WARNING, FocusLimits.quotaLevel(usedMinutes = 21, quotaMinutes = 30)) // 70.0%
        assertEquals(FocusLimits.QuotaLevel.WARNING, FocusLimits.quotaLevel(usedMinutes = 29, quotaMinutes = 30)) // 96.6%

        // >= 100% is EXHAUSTED
        assertEquals(FocusLimits.QuotaLevel.EXHAUSTED, FocusLimits.quotaLevel(usedMinutes = 30, quotaMinutes = 30))
        assertEquals(FocusLimits.QuotaLevel.EXHAUSTED, FocusLimits.quotaLevel(usedMinutes = 35, quotaMinutes = 30))

        // Edge case: zero or negative quota
        assertEquals(FocusLimits.QuotaLevel.EXHAUSTED, FocusLimits.quotaLevel(usedMinutes = 0, quotaMinutes = 0))
    }

    @Test
    fun `preset matching identifies predefined presets and rejects custom profiles`() {
        // BALANCED
        val matchedBalanced = FocusLimits.findMatchingPreset(10, 5, 10, 30)
        assertEquals(FocusPreset.BALANCED, matchedBalanced)
        assertTrue(FocusPreset.BALANCED.matches(10, 5, 10, 30))

        // DEEP_FOCUS
        val matchedDeep = FocusLimits.findMatchingPreset(20, 3, 20, 15)
        assertEquals(FocusPreset.DEEP_FOCUS, matchedDeep)
        assertTrue(FocusPreset.DEEP_FOCUS.matches(20, 3, 20, 15))

        // LIGHT_DETOX
        val matchedLight = FocusLimits.findMatchingPreset(5, 10, 5, 60)
        assertEquals(FocusPreset.LIGHT_DETOX, matchedLight)
        assertTrue(FocusPreset.LIGHT_DETOX.matches(5, 10, 5, 60))

        // Custom
        val custom = FocusLimits.findMatchingPreset(12, 6, 15, 45)
        assertNull(custom)
        assertFalse(FocusPreset.BALANCED.matches(12, 6, 15, 45))
    }

    @Test
    fun `sanitizeAnchorHabit handles null, blank, newlines and length truncation`() {
        // Null or blank falls back to default
        assertEquals(FocusConfig.DEFAULT_ANCHOR_HABIT, FocusLimits.sanitizeAnchorHabit(null))
        assertEquals(FocusConfig.DEFAULT_ANCHOR_HABIT, FocusLimits.sanitizeAnchorHabit(""))
        assertEquals(FocusConfig.DEFAULT_ANCHOR_HABIT, FocusLimits.sanitizeAnchorHabit("   \n\t  "))

        // Replaces newlines and carriage returns with spaces
        assertEquals("Ler um livro de tarde", FocusLimits.sanitizeAnchorHabit("Ler um livro\nde tarde\r"))

        // Truncates strings exceeding 40 characters
        val longText = "Esta frase tem mais de quarenta caracteres com certeza absoluta"
        val sanitized = FocusLimits.sanitizeAnchorHabit(longText)
        assertTrue(sanitized.length <= FocusLimits.MAX_ANCHOR_HABIT_LENGTH)
        assertEquals("Esta frase tem mais de quarenta caracter", sanitized)

        // Valid strings under 40 chars preserved exactly (with outer trim)
        val normal = "  Beber 500ml de água 💧  "
        assertEquals("Beber 500ml de água 💧", FocusLimits.sanitizeAnchorHabit(normal))
    }

    @Test
    fun `computeSessionChoices returns distinct sorted budgets fitting remaining quota`() {
        assertEquals(listOf(1, 3, 5), FocusLimits.computeSessionChoices(maxSessionMinutes = 5, remainingQuotaSeconds = 600L))
        assertEquals(listOf(1, 3), FocusLimits.computeSessionChoices(maxSessionMinutes = 5, remainingQuotaSeconds = 180L))
        assertEquals(listOf(1, 2), FocusLimits.computeSessionChoices(maxSessionMinutes = 5, remainingQuotaSeconds = 120L))
        assertEquals(listOf(1), FocusLimits.computeSessionChoices(maxSessionMinutes = 1, remainingQuotaSeconds = 600L))
        assertEquals(listOf(1), FocusLimits.computeSessionChoices(maxSessionMinutes = 5, remainingQuotaSeconds = 45L))
        assertEquals(emptyList<Int>(), FocusLimits.computeSessionChoices(maxSessionMinutes = 5, remainingQuotaSeconds = 0L))
    }

    @Test
    fun `FocusGroup effectiveDailyQuotaMinutes interpolates linearly across 21 days`() {
        val group = FocusGroup(
            id = "social",
            name = "Redes",
            sessionDurationMinutes = 5,
            dailyQuotaMinutes = 62,
            taperingEnabled = true,
            taperingTargetQuotaMinutes = 20,
            taperingStartEpochDay = 100L
        )
        assertEquals(62, group.effectiveDailyQuotaMinutes(99L))
        assertEquals(62, group.effectiveDailyQuotaMinutes(100L))
        // 42 min total reduction over 21 days = -2 min per day
        assertEquals(60, group.effectiveDailyQuotaMinutes(101L))
        assertEquals(42, group.effectiveDailyQuotaMinutes(110L))
        assertEquals(20, group.effectiveDailyQuotaMinutes(121L))
        assertEquals(20, group.effectiveDailyQuotaMinutes(150L))
    }

    @Test
    fun `FocusGroup quiet hours supports overnight and same day ranges`() {
        val overnight = FocusGroup(
            id = "social",
            name = "Redes",
            quietHoursEnabled = true,
            quietHoursStartMinute = 22 * 60 + 30, // 22:30 (1350)
            quietHoursEndMinute = 7 * 60 + 30     // 07:30 (450)
        )
        assertTrue(overnight.isWithinQuietHours(22 * 60 + 30))
        assertTrue(overnight.isWithinQuietHours(23 * 60 + 59))
        assertTrue(overnight.isWithinQuietHours(0))
        assertTrue(overnight.isWithinQuietHours(7 * 60 + 29))
        assertFalse(overnight.isWithinQuietHours(7 * 60 + 30))
        assertFalse(overnight.isWithinQuietHours(14 * 60))

        // At 23:30 (1410), 8 hours (480 min = 28800s) remain until 07:30 (450)
        assertEquals(480L * 60L, overnight.remainingQuietHoursSeconds(23 * 60 + 30))
        // At 06:30 (390), 1 hour (60 min = 3600s) remains until 07:30 (450)
        assertEquals(60L * 60L, overnight.remainingQuietHoursSeconds(6 * 60 + 30))
    }
}
