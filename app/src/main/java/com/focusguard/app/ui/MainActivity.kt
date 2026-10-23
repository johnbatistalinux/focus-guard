package com.focusguard.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.focusguard.app.R
import com.focusguard.app.data.FocusPreferencesStore
import com.focusguard.app.data.FocusRuntime
import com.focusguard.app.data.SystemTimeProvider
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.FocusPolicyEngine
import com.focusguard.app.domain.SocialAppCatalog
import com.focusguard.app.domain.TargetApp
import com.focusguard.app.service.FocusAccessibilityService
import com.focusguard.app.ui.FocusTheme.dp

/**
 * Lean main configuration screen for FocusGuard (< 250 lines).
 * Follows the flat, dark-only design system and coordinates
 * modular cards, bottom sheets, and anti-bypass lock enforcement.
 */
class MainActivity : Activity() {

    private lateinit var store: FocusPreferencesStore
    private lateinit var policyEngine: FocusPolicyEngine

    private lateinit var rootScrollView: ScrollView
    private lateinit var statusPillText: TextView
    private lateinit var serviceCalloutView: ServiceCalloutView
    private lateinit var lockBannerText: TextView
    private lateinit var summaryCardView: SummaryCardView
    private lateinit var groupsContainer: LinearLayout
    private lateinit var anchorHabitCardView: AnchorHabitCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FocusRuntime.getStore(this)
        policyEngine = FocusRuntime.getEngine(this)
        setContentView(buildRootView())
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun buildRootView(): View {
        rootScrollView = ScrollView(this).apply {
            setBackgroundColor(FocusTheme.COLOR_CANVAS)
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(20)
            setPadding(padH, dp(16), padH, dp(32))
        }
        FocusTheme.applySystemWindowInsetsPadding(content, applyTop = true, applyBottom = true)

        // 1. Top Bar: FocusGuard title + Status Pill
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }
        val titleView = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        statusPillText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(36)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { openAccessibilitySettings() }
        }
        topBar.addView(titleView)
        topBar.addView(statusPillText)
        content.addView(topBar)

        // 2. Service Callout (Visible only when Accessibility is disabled)
        serviceCalloutView = ServiceCalloutView(this) { openAccessibilitySettings() }
        content.addView(serviceCalloutView)

        // 3. Lock Banner (Visible only when anti-bypass lock is active)
        lockBannerText = TextView(this).apply {
            setTextColor(FocusTheme.COLOR_DANGER)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = FocusTheme.flatBox(FocusTheme.COLOR_DANGER_SUBTLE, cornerDp = 12)
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
        content.addView(lockBannerText)

        // 4. Daily Summary Card
        summaryCardView = SummaryCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(20)
            }
        }
        content.addView(summaryCardView)

        // 5. Groups Section
        content.addView(sectionHeader(getString(R.string.groups_title)))
        groupsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(groupsContainer)

        val createGroupBtn = Button(this).apply {
            text = getString(R.string.create_group_action)
            isAllCaps = false
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(48)
            background = FocusTheme.outlineRipple(FocusTheme.COLOR_ELEVATED, strokeWidthDp = 1, cornerDp = 12)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(24)
            }
            setOnClickListener { openCreateGroupSheet() }
        }
        content.addView(createGroupBtn)

        // 6. Preferences Section
        content.addView(sectionHeader(getString(R.string.preferences_title)))
        anchorHabitCardView = AnchorHabitCardView(this) { openAnchorHabitSheet() }
        content.addView(anchorHabitCardView)

        rootScrollView.addView(content)
        return rootScrollView
    }

    private fun refreshUi() {
        val savedScrollY = rootScrollView.scrollY
        val isA11yActive = FocusAccessibilityService.isServiceRunning() || isAccessibilityServiceEnabledInSettings()

        if (isA11yActive) {
            statusPillText.text = "● ${getString(R.string.status_protected)}"
            statusPillText.setTextColor(FocusTheme.COLOR_ACCENT)
            statusPillText.background = FocusTheme.flatBox(FocusTheme.COLOR_ACCENT_SUBTLE, cornerDp = 10)
            serviceCalloutView.visibility = View.GONE
        } else {
            statusPillText.text = "○ ${getString(R.string.status_inactive)}"
            statusPillText.setTextColor(FocusTheme.COLOR_WARN)
            statusPillText.background = FocusTheme.flatBox(FocusTheme.COLOR_WARN_SUBTLE, cornerDp = 10)
            serviceCalloutView.visibility = View.VISIBLE
        }

        val lockStatus = policyEngine.getSettingsLockStatus()
        lockBannerText.visibility = if (lockStatus.isLocked) View.VISIBLE else View.GONE
        if (lockStatus.isLocked) lockBannerText.text = "🔒 ${lockStatus.reasonMessage}"

        summaryCardView.bind(policyEngine.getRewardSummary())
        val config = store.getConfig()
        anchorHabitCardView.setAnchorHabit(config.selectedAnchorHabit)
        renderGroups(config, isLocked = lockStatus.isLocked)
        rootScrollView.post { rootScrollView.scrollTo(0, savedScrollY) }
    }

    private fun renderGroups(config: FocusConfig, isLocked: Boolean) {
        groupsContainer.removeAllViews()
        val today = SystemTimeProvider.currentEpochDay()
        val nowMs = SystemTimeProvider.elapsedRealtimeMs()

        for (group in config.getEffectiveGroups()) {
            val groupUsage = store.getUsageState("group_${group.id}", today)
            val isCooldown = group.sharedQuota && groupUsage.cooldownUntilRealtimeMs > nowMs &&
                (groupUsage.cooldownUntilRealtimeMs - nowMs) <= group.cooldownSeconds * 1000L

            var totalUsedSec = if (group.sharedQuota) groupUsage.usedDailySeconds else 0L
            val appsStats = HashMap<String, Pair<Long, Int>>(group.appIds.size)
            val resolvedApps = ArrayList<TargetApp>(group.appIds.size)

            for (appId in group.appIds) {
                val app = SocialAppCatalog.findById(appId, config.customApps)
                    ?: TargetApp(id = appId, displayName = appId, packageNames = setOf(appId), isCustom = true)
                resolvedApps.add(app)
                val u = store.getUsageState(appId, today)
                appsStats[appId] = Pair(u.usedDailySeconds / 60L, u.sessionsStartedToday)
                totalUsedSec = if (group.sharedQuota) maxOf(totalUsedSec, u.usedDailySeconds) else totalUsedSec + u.usedDailySeconds
            }

            val card = GroupCardView(
                this, group, isLocked,
                onToggleEnabled = { enabled ->
                    val updated = config.getEffectiveGroups().map { if (it.id == group.id) it.copy(enabled = enabled) else it }
                    saveGroups(config, updated)
                },
                onAdjustLimitsClicked = { openGroupConfigSheet(group, config, isLocked) },
                onManageAppsClicked = { openAppPickerSheet(group, config) },
                onLockedActionAttempt = { notifyLockedAttempt() }
            )
            card.bindData(totalUsedSec / 60L, group.dailyQuotaMinutes, isCooldown, resolvedApps, appsStats)
            groupsContainer.addView(card)
        }
    }

    private fun sectionHeader(title: String): TextView = TextView(this).apply {
        text = title.uppercase()
        setTextColor(FocusTheme.COLOR_TEXT_MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(14), 0, dp(8))
    }

    private fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun openAnchorHabitSheet() {
        if (policyEngine.getSettingsLockStatus().isLocked) { notifyLockedAttempt(); return }
        val config = store.getConfig()
        AnchorHabitSheet(this, config.selectedAnchorHabit) { updateConfig(config.copy(selectedAnchorHabit = it)) }.show()
    }

    private fun openGroupConfigSheet(group: FocusGroup, config: FocusConfig, isLocked: Boolean) {
        if (isLocked) { notifyLockedAttempt(); return }
        GroupConfigSheet(this, group, isNewGroup = false, isLocked = isLocked,
            onSave = { updated ->
                val list = config.getEffectiveGroups().toMutableList()
                val idx = list.indexOfFirst { it.id == updated.id }
                if (idx >= 0) list[idx] = updated else list.add(updated)
                saveGroups(config, list)
            },
            onDelete = { del -> saveGroups(config, config.getEffectiveGroups().filter { it.id != del.id }) }
        ).show()
    }

    private fun openCreateGroupSheet() {
        if (policyEngine.getSettingsLockStatus().isLocked) { notifyLockedAttempt(); return }
        val config = store.getConfig()
        val newGroup = FocusGroup("group_${System.currentTimeMillis()}", "Nova Categoria", "🎯", true, emptySet())
        GroupConfigSheet(this, newGroup, isNewGroup = true, isLocked = false, onSave = { created ->
            val list = config.getEffectiveGroups().toMutableList().apply { add(created) }
            saveGroups(config, list)
        }).show()
    }

    private fun openAppPickerSheet(group: FocusGroup, config: FocusConfig) {
        if (policyEngine.getSettingsLockStatus().isLocked) { notifyLockedAttempt(); return }
        AppPickerSheet(this, group, config, onAppsConfirmed = { selectedIds, allCustom ->
            val list = config.getEffectiveGroups().map { if (it.id == group.id) it.copy(appIds = selectedIds) else it.copy(appIds = it.appIds - selectedIds) }
            saveGroups(config, list, allCustom)
        }).show()
    }

    private fun saveGroups(config: FocusConfig, groups: List<FocusGroup>, customApps: List<TargetApp> = config.customApps) {
        val allEnabled = groups.filter { it.enabled }.flatMap { it.appIds }.toSet()
        updateConfig(config.copy(groups = groups, customApps = customApps, enabledAppIds = allEnabled))
    }

    private fun notifyLockedAttempt() {
        Toast.makeText(this, policyEngine.getSettingsLockStatus().reasonMessage ?: getString(R.string.lock_banner_title), Toast.LENGTH_SHORT).show()
    }

    private fun updateConfig(newConfig: FocusConfig) {
        if (policyEngine.getSettingsLockStatus().isLocked) return
        store.saveConfig(newConfig)
        FocusAccessibilityService.notifyConfigUpdated()
        refreshUi()
    }

    private fun isAccessibilityServiceEnabledInSettings(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(packageName)
    }
}
