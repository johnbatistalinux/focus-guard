package com.focusguard.app.ui.presenter

import com.focusguard.app.data.FocusPreferencesStore
import com.focusguard.app.data.SystemTimeProvider
import com.focusguard.app.domain.ActiveSessionSnapshot
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.FocusLimits
import com.focusguard.app.domain.FocusPolicyEngine
import com.focusguard.app.domain.FocusRewardSummary
import com.focusguard.app.domain.SettingsLockStatus
import com.focusguard.app.domain.SocialAppCatalog
import com.focusguard.app.domain.TargetApp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AppUsageRow(
    val app: TargetApp,
    val usedMinutes: Long,
    val sessions: Int
)

data class GroupLimitState(
    val group: FocusGroup,
    val usedMinutes: Long,
    val quotaMinutes: Int,
    val remainingMinutes: Long,
    val level: FocusLimits.QuotaLevel,
    val isCooldown: Boolean,
    val cooldownRemainingSeconds: Long,
    val apps: List<AppUsageRow>
)

data class TodayState(
    val summary: FocusRewardSummary,
    val groups: List<GroupLimitState>,
    val activeSession: ActiveSessionSnapshot?,
    val lock: SettingsLockStatus,
    val serviceActive: Boolean,
    val epochDay: Long
)

data class SettingsState(
    val config: FocusConfig,
    val lock: SettingsLockStatus,
    val serviceActive: Boolean
)

/**
 * Maps the policy engine and persistence stores into immutable presentation state.
 * Keeps screens free of storage queries and makes formatting unit-testable.
 */
class DashboardPresenter(
    private val store: FocusPreferencesStore,
    private val engine: FocusPolicyEngine
) {

    private val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale("pt", "BR"))

    fun buildTodayState(serviceActive: Boolean): TodayState {
        val today = SystemTimeProvider.currentEpochDay()
        val nowMs = SystemTimeProvider.elapsedRealtimeMs()
        val config = store.getConfig()
        return TodayState(
            summary = engine.getRewardSummary(),
            groups = config.getEffectiveGroups().map { buildGroupState(it, config, today, nowMs) },
            activeSession = engine.getActiveSessionSnapshot(),
            lock = engine.getSettingsLockStatus(),
            serviceActive = serviceActive,
            epochDay = today
        )
    }

    fun buildSettingsState(serviceActive: Boolean): SettingsState = SettingsState(
        config = store.getConfig(),
        lock = engine.getSettingsLockStatus(),
        serviceActive = serviceActive
    )

    fun buildGroupState(group: FocusGroup, config: FocusConfig): GroupLimitState {
        val today = SystemTimeProvider.currentEpochDay()
        val nowMs = SystemTimeProvider.elapsedRealtimeMs()
        return buildGroupState(group, config, today, nowMs)
    }

    private fun buildGroupState(
        group: FocusGroup,
        config: FocusConfig,
        today: Long,
        nowMs: Long
    ): GroupLimitState {
        val groupUsage = store.getUsageState("group_${group.id}", today)
        var usedSeconds = if (group.sharedQuota) groupUsage.usedDailySeconds else 0L
        var cooldownUntilMs = groupUsage.cooldownUntilRealtimeMs
        val apps = ArrayList<AppUsageRow>(group.appIds.size)

        for (appId in group.appIds) {
            val app = SocialAppCatalog.findById(appId, config.customApps)
                ?: TargetApp(
                    id = appId,
                    displayName = appId,
                    packageNames = setOf(appId),
                    isCustom = true
                )
            val usage = store.getUsageState(appId, today)
            if (group.sharedQuota) {
                usedSeconds = maxOf(usedSeconds, usage.usedDailySeconds)
            } else {
                usedSeconds += usage.usedDailySeconds
            }
            cooldownUntilMs = maxOf(cooldownUntilMs, usage.cooldownUntilRealtimeMs)
            apps.add(
                AppUsageRow(
                    app = app,
                    usedMinutes = usage.usedDailySeconds / 60L,
                    sessions = usage.sessionsStartedToday
                )
            )
        }

        apps.sortBy { it.app.displayName.lowercase() }
        val usedMinutes = usedSeconds / 60L
        val effectiveQuotaMinutes = group.effectiveDailyQuotaMinutes(today)
        val cooldownRemainingSeconds = if (cooldownUntilMs > nowMs) {
            (cooldownUntilMs - nowMs + 999L) / 1000L
        } else {
            0L
        }
        return GroupLimitState(
            group = group,
            usedMinutes = usedMinutes,
            quotaMinutes = effectiveQuotaMinutes,
            remainingMinutes = (effectiveQuotaMinutes - usedMinutes).coerceAtLeast(0L),
            level = FocusLimits.quotaLevel(usedMinutes, effectiveQuotaMinutes),
            isCooldown = cooldownRemainingSeconds in 1..(group.cooldownSeconds + 5L),
            cooldownRemainingSeconds = cooldownRemainingSeconds,
            apps = apps
        )
    }

    /**
     * Six weekday abbreviations plus an empty slot for today, which the card
     * localizes itself so copy never lives in the presentation layer.
     */
    fun dayLabels(epochDay: Long): List<String> = (6 downTo 0).map { offset ->
        if (offset == 0) {
            ""
        } else {
            LocalDate.ofEpochDay(epochDay - offset)
                .format(dayFormatter)
                .replace(".", "")
                .take(3)
        }
    }
}
