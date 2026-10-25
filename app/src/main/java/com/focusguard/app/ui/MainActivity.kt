package com.focusguard.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.focusguard.app.R
import com.focusguard.app.data.FocusPreferencesStore
import com.focusguard.app.data.FocusRuntime
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.FocusPolicyEngine
import com.focusguard.app.domain.GroupIcon
import com.focusguard.app.domain.TargetApp
import com.focusguard.app.service.FocusAccessibilityService
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.presenter.DashboardPresenter
import com.focusguard.app.ui.design.hapticLight

/**
 * Three-tab application shell: Hoje, Grupos and Ajustes.
 *
 * Owns navigation, configuration mutations, anti-bypass lock enforcement and
 * the onboarding redirect. All visual hierarchy lives in the screen classes.
 */
class MainActivity : Activity() {

    private lateinit var store: FocusPreferencesStore
    private lateinit var policyEngine: FocusPolicyEngine
    private lateinit var presenter: DashboardPresenter

    private lateinit var todayScreen: TodayScreen
    private lateinit var groupsScreen: GroupsScreen
    private lateinit var settingsScreen: SettingsScreen
    private lateinit var navBar: LinearLayout
    private val navItems = ArrayList<NavItem>()

    private var currentTab: Int = TAB_TODAY
    private var isReady: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FocusRuntime.getStore(this)
        policyEngine = FocusRuntime.getEngine(this)
        presenter = DashboardPresenter(store, policyEngine)

        if (!store.isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(buildRootView())
        isReady = true
        selectTab(TAB_TODAY)
    }

    override fun onResume() {
        super.onResume()
        if (!isReady) return
        if (!store.isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        refreshUi()
    }

    @Suppress("DEPRECATION")
    private fun enableEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    private fun buildRootView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.CANVAS)
        }

        val screens = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        todayScreen = TodayScreen(
            this,
            onOpenAccessibilitySettings = { openAccessibilitySettings() },
            onTestBarrier = { showBarrierPreview() },
            onViewGroups = { selectTab(TAB_GROUPS) },
            onCreateGroup = { openCreateGroupSheet() },
            onEndSession = { endActiveSession() }
        )
        groupsScreen = GroupsScreen(
            this,
            onNewGroup = { openCreateGroupSheet() },
            onToggleGroup = { group, enabled -> setGroupEnabled(group, enabled) },
            onEditGroup = { group -> openGroupConfigSheet(group) },
            onManageApps = { group -> openAppPickerSheet(group) },
            onLockedAttempt = { notifyLockedAttempt() }
        )
        settingsScreen = SettingsScreen(
            this,
            onOpenAccessibilitySettings = { openAccessibilitySettings() },
            onTestBarrier = { showBarrierPreview() },
            onChangeAnchorHabit = { openAnchorHabitSheet() },
            onResetToday = { confirmResetToday() },
            onResetAll = { confirmResetAll() },
            onLockedAttempt = { notifyLockedAttempt() }
        )

        screens.addView(todayScreen)
        screens.addView(groupsScreen)
        screens.addView(settingsScreen)
        root.addView(screens)

        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.SURFACE)
        }
        val hairline = View(this).apply {
            setBackgroundColor(Palette.HAIRLINE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        }
        navBar.addView(hairline)
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(Space.S), dp(Space.XS), dp(Space.S), dp(Space.XS))
        }
        navItems.add(navItem(R.drawable.ic_today, R.string.nav_today, TAB_TODAY))
        navItems.add(navItem(R.drawable.ic_groups, R.string.nav_groups, TAB_GROUPS))
        navItems.add(navItem(R.drawable.ic_settings, R.string.nav_settings, TAB_SETTINGS))
        for (item in navItems) navRow.addView(item.container)
        navBar.addView(navRow)
        Theme.applyInsets(navBar, applyTop = false, applyBottom = true, applyLeft = true, applyRight = true)
        root.addView(navBar)

        return root
    }

    private inner class NavItem(
        val container: LinearLayout,
        val icon: ImageView,
        val label: TextView
    )

    private fun navItem(iconRes: Int, labelRes: Int, tabIndex: Int): NavItem {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minimumHeight = dp(56)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { view ->
                view.hapticLight()
                selectTab(tabIndex)
            }
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        }
        val label = Theme.text(
            this,
            getString(labelRes),
            TypeScale.CAPTION_SP,
            Palette.TEXT_MUTED,
            Theme.MEDIUM,
            gravity = Gravity.CENTER
        ).apply {
            setPadding(0, dp(3), 0, 0)
        }
        container.addView(icon)
        container.addView(label)
        return NavItem(container, icon, label)
    }

    private fun selectTab(index: Int) {
        if (!isReady) return
        currentTab = index
        todayScreen.visibility = if (index == TAB_TODAY) View.VISIBLE else View.GONE
        groupsScreen.visibility = if (index == TAB_GROUPS) View.VISIBLE else View.GONE
        settingsScreen.visibility = if (index == TAB_SETTINGS) View.VISIBLE else View.GONE
        for ((itemIndex, item) in navItems.withIndex()) {
            val selected = itemIndex == index
            item.icon.setColorFilter(
                if (selected) Palette.ACCENT else Palette.TEXT_MUTED,
                PorterDuff.Mode.SRC_IN
            )
            item.label.setTextColor(if (selected) Palette.ACCENT else Palette.TEXT_MUTED)
        }
        refreshUi()
    }

    private fun refreshUi() {
        val serviceActive = FocusAccessibilityService.isServiceRunning() ||
            isAccessibilityServiceEnabledInSettings()

        when (currentTab) {
            TAB_TODAY -> {
                val state = presenter.buildTodayState(serviceActive)
                todayScreen.bind(state, presenter.dayLabels(state.epochDay))
            }
            TAB_GROUPS -> {
                groupsScreen.bind(presenter.buildTodayState(serviceActive))
            }
            TAB_SETTINGS -> {
                settingsScreen.bind(presenter.buildSettingsState(serviceActive))
            }
        }
    }

    private fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.recoverCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }.onFailure {
            toast(getString(R.string.service_test_unavailable))
        }
    }

    private fun showBarrierPreview() {
        if (!FocusAccessibilityService.isServiceRunning() || !FocusAccessibilityService.showBarrierPreview()) {
            toast(getString(R.string.service_test_unavailable))
        }
    }

    private fun endActiveSession() {
        if (!FocusAccessibilityService.endSessionFromDashboard()) {
            toast(getString(R.string.today_session_end))
        }
        refreshUi()
    }

    private fun openAnchorHabitSheet() {
        if (policyEngine.getSettingsLockStatus().isLocked) {
            notifyLockedAttempt()
            return
        }
        val config = store.getConfig()
        AnchorHabitSheet(this, config.selectedAnchorHabit) { habit ->
            updateConfig(config.copy(selectedAnchorHabit = habit))
        }.show()
    }

    private fun openGroupConfigSheet(group: FocusGroup) {
        if (policyEngine.getSettingsLockStatus().isLocked) {
            notifyLockedAttempt()
            return
        }
        val config = store.getConfig()
        val locked = policyEngine.getSettingsLockStatus().isLocked
        GroupConfigSheet(
            this,
            group,
            isNewGroup = false,
            isLocked = locked,
            onSave = { updated ->
                val list = config.getEffectiveGroups().toMutableList()
                val index = list.indexOfFirst { it.id == updated.id }
                if (index >= 0) list[index] = updated else list.add(updated)
                saveGroups(config, list)
            },
            onDelete = { deleted ->
                saveGroups(config, config.getEffectiveGroups().filter { it.id != deleted.id })
            }
        ).show()
    }

    private fun openCreateGroupSheet() {
        if (policyEngine.getSettingsLockStatus().isLocked) {
            notifyLockedAttempt()
            return
        }
        val config = store.getConfig()
        val newGroup = FocusGroup(
            id = "group_${System.currentTimeMillis()}",
            name = getString(R.string.group_created_name),
            iconKey = GroupIcon.CUSTOM.key,
            enabled = true,
            appIds = emptySet()
        )
        GroupConfigSheet(
            this,
            newGroup,
            isNewGroup = true,
            isLocked = false,
            onSave = { created ->
                val list = config.getEffectiveGroups().toMutableList().apply { add(created) }
                saveGroups(config, list)
                if (created.appIds.isEmpty()) {
                    openAppPickerSheet(created)
                }
            }
        ).show()
    }

    private fun openAppPickerSheet(group: FocusGroup) {
        if (policyEngine.getSettingsLockStatus().isLocked) {
            notifyLockedAttempt()
            return
        }
        val config = store.getConfig()
        AppPickerSheet(this, group, config, onAppsConfirmed = { selectedIds, allCustom ->
            val list = config.getEffectiveGroups().map { other ->
                if (other.id == group.id) {
                    other.copy(appIds = selectedIds)
                } else {
                    other.copy(appIds = other.appIds - selectedIds)
                }
            }
            saveGroups(config, list, allCustom)
        }).show()
    }

    private fun setGroupEnabled(group: FocusGroup, enabled: Boolean) {
        if (policyEngine.getSettingsLockStatus().isLocked) {
            notifyLockedAttempt()
            return
        }
        val config = store.getConfig()
        val list = config.getEffectiveGroups().map {
            if (it.id == group.id) it.copy(enabled = enabled) else it
        }
        saveGroups(config, list)
    }

    private fun confirmResetToday() {
        ConfirmSheet(
            this,
            getString(R.string.reset_today_title),
            getString(R.string.reset_today_body),
            getString(R.string.reset_today_action)
        ) {
            store.resetTodayStats()
            FocusAccessibilityService.notifyConfigUpdated()
            refreshUi()
        }.show()
    }

    private fun confirmResetAll() {
        ConfirmSheet(
            this,
            getString(R.string.reset_all_title),
            getString(R.string.reset_all_body),
            getString(R.string.reset_all_action)
        ) {
            store.resetAllStats()
            FocusAccessibilityService.notifyConfigUpdated()
            refreshUi()
        }.show()
    }

    private fun saveGroups(
        config: FocusConfig,
        groups: List<FocusGroup>,
        customApps: List<TargetApp> = config.customApps
    ) {
        val allEnabled = groups.filter { it.enabled }.flatMap { it.appIds }.toSet()
        updateConfig(config.copy(groups = groups, customApps = customApps, enabledAppIds = allEnabled))
    }

    private fun updateConfig(newConfig: FocusConfig) {
        if (policyEngine.getSettingsLockStatus().isLocked) {
            notifyLockedAttempt()
            return
        }
        store.saveConfig(newConfig)
        FocusAccessibilityService.notifyConfigUpdated()
        refreshUi()
    }

    private fun notifyLockedAttempt() {
        toast(policyEngine.getSettingsLockStatus().reasonMessage ?: getString(R.string.lock_banner_title))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isAccessibilityServiceEnabledInSettings(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName)
    }

    companion object {
        private const val TAB_TODAY = 0
        private const val TAB_GROUPS = 1
        private const val TAB_SETTINGS = 2
    }
}
