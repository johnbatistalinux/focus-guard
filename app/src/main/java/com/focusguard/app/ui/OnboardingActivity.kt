package com.focusguard.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.focusguard.app.R
import com.focusguard.app.data.FocusPreferencesStore
import com.focusguard.app.data.FocusRuntime
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.FocusPreset
import com.focusguard.app.domain.GroupIcon
import com.focusguard.app.domain.SocialAppCatalog
import com.focusguard.app.service.FocusAccessibilityService
import com.focusguard.app.ui.design.ButtonStyle
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.button
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.matchWrap
import com.focusguard.app.ui.design.monogram
import com.focusguard.app.ui.design.themedCheckBox
import com.focusguard.app.ui.design.hapticLight

/**
 * Three-step first-run wizard: activate the accessibility service, choose the
 * apps worth monitoring, then pick a starting rhythm. Existing installations
 * are redirected straight to [MainActivity].
 */
class OnboardingActivity : Activity() {

    private lateinit var store: FocusPreferencesStore
    private var step: Int = 0
    private val selectedAppIds = LinkedHashSet<String>()
    private var selectedPreset: FocusPreset = FocusPreset.BALANCED

    private lateinit var progressRow: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var footer: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FocusRuntime.getStore(this)
        if (store.isOnboardingCompleted()) {
            goToDashboard()
            return
        }
        enableEdgeToEdge()
        setContentView(buildRoot())
        renderStep()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized && step == 0) renderStep()
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

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.CANVAS)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Space.XL), dp(Space.XL), dp(Space.XL), dp(Space.S))
        }
        Theme.applyInsets(header, applyTop = true, applyBottom = false)
        progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        header.addView(progressRow)
        root.addView(header)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Space.XL), dp(Space.M), dp(Space.XL), dp(Space.XXL))
        }
        scrollView = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(content)
        }
        root.addView(scrollView)

        footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Space.XL), dp(Space.M), dp(Space.XL), dp(Space.M))
        }
        Theme.applyInsets(footer, applyTop = false, applyBottom = true)
        root.addView(footer)
        return root
    }

    private fun renderStep() {
        renderProgress()
        content.removeAllViews()
        footer.removeAllViews()
        when (step) {
            0 -> renderActivateStep()
            1 -> renderAppsStep()
            else -> renderRhythmStep()
        }
    }

    private fun renderProgress() {
        progressRow.removeAllViews()
        for (index in 0 until TOTAL_STEPS) {
            val segment = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(3), 1f).apply {
                    marginEnd = dp(if (index == TOTAL_STEPS - 1) 0 else Space.XS)
                }
                background = Theme.box(
                    if (index <= step) Palette.ACCENT else Palette.ELEVATED,
                    Radius.PILL
                )
            }
            progressRow.addView(segment)
        }
        progressRow.addView(
            Theme.text(
                this,
                getString(R.string.onboarding_step, step + 1, TOTAL_STEPS),
                TypeScale.CAPTION_SP,
                Palette.TEXT_MUTED
            ).apply {
                setPadding(dp(Space.M), 0, 0, 0)
            }
        )
    }

    private fun renderActivateStep() {
        addTitle(getString(R.string.onboarding_activate_title))
        addBody(getString(R.string.onboarding_activate_body))

        val serviceActive = FocusAccessibilityService.isServiceRunning() ||
            isAccessibilityServiceEnabledInSettings()

        val card = ServiceStatusCard(
            this,
            detailed = true,
            onActivate = { openAccessibilitySettings() },
            onTest = {
                if (!FocusAccessibilityService.showBarrierPreview()) {
                    Toast.makeText(this, R.string.service_test_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        ).apply {
            layoutParams = matchWrap().apply { topMargin = dp(Space.L) }
        }
        card.bind(serviceActive)
        content.addView(card)

        footer.addView(
            button(this, getString(R.string.onboarding_next), ButtonStyle.PRIMARY) {
                step = 1
                renderStep()
            }
        )
    }

    private fun renderAppsStep() {
        addTitle(getString(R.string.onboarding_apps_title))
        addBody(getString(R.string.onboarding_apps_body))

        val counter = Theme.text(
            this,
            getString(R.string.onboarding_apps_selected, selectedAppIds.size),
            TypeScale.LABEL_SP,
            Palette.ACCENT,
            Theme.MEDIUM
        ).apply {
            setPadding(0, dp(Space.M), 0, dp(Space.S))
        }
        content.addView(counter)

        for (app in SocialAppCatalog.SUPPORTED_APPS) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                isClickable = true
                isFocusable = true
                layoutParams = matchWrap()
            }
            row.addView(
                monogram(this, app.displayName, app.brandColorHex, sizeDp = 34).apply {
                    layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                        marginEnd = dp(Space.M)
                    }
                }
            )
            row.addView(
                Theme.text(this, app.displayName, TypeScale.BODY_SP, Palette.TEXT_PRIMARY).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            val check = themedCheckBox(this, app.id in selectedAppIds)
            row.addView(check)
            row.setOnClickListener { view ->
                view.hapticLight()
                if (selectedAppIds.contains(app.id)) {
                    selectedAppIds.remove(app.id)
                    check.isChecked = false
                } else {
                    selectedAppIds.add(app.id)
                    check.isChecked = true
                }
                counter.text = getString(R.string.onboarding_apps_selected, selectedAppIds.size)
            }
            content.addView(row)
        }

        footer.addView(
            button(this, getString(R.string.onboarding_next), ButtonStyle.PRIMARY) {
                step = 2
                renderStep()
            }
        )
        footer.addView(
            button(this, getString(R.string.action_skip), ButtonStyle.GHOST) {
                selectedAppIds.clear()
                step = 2
                renderStep()
            }.apply {
                layoutParams = matchWrap().apply { topMargin = dp(Space.XS) }
            }
        )
    }

    private fun renderRhythmStep() {
        addTitle(getString(R.string.onboarding_rhythm_title))
        addBody(getString(R.string.onboarding_rhythm_body))

        for (preset in FocusPreset.entries) {
            val selected = preset == selectedPreset
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Theme.ripple(
                    if (selected) Palette.ACCENT_SUBTLE else Palette.SURFACE,
                    Radius.CARD
                )
                val pad = dp(Space.L)
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                layoutParams = matchWrap().apply { bottomMargin = dp(Space.S) }
                setOnClickListener { view ->
                    view.hapticLight()
                    selectedPreset = preset
                    renderStep()
                }
            }
            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(
                Theme.text(
                    this,
                    preset.title,
                    TypeScale.TITLE_M_SP,
                    if (selected) Palette.ACCENT else Palette.TEXT_PRIMARY,
                    Theme.MEDIUM
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            if (selected) {
                titleRow.addView(
                    ImageView(this).apply {
                        setImageResource(R.drawable.ic_check)
                        setColorFilter(Palette.ACCENT, PorterDuff.Mode.SRC_IN)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                    }
                )
            }
            card.addView(titleRow)
            card.addView(
                Theme.text(
                    this,
                    preset.description,
                    TypeScale.CAPTION_SP,
                    Palette.TEXT_MUTED
                ).apply {
                    setPadding(0, dp(Space.XS), 0, 0)
                }
            )
            content.addView(card)
        }

        footer.addView(
            button(this, getString(R.string.onboarding_finish), ButtonStyle.PRIMARY) {
                finishOnboarding()
            }
        )
    }

    private fun finishOnboarding() {
        val preset = selectedPreset
        val config = store.getConfig()
        val groups = if (selectedAppIds.isEmpty()) {
            emptyList()
        } else {
            listOf(
                FocusGroup(
                    id = FocusGroup.DEFAULT_GROUP_SOCIAL_ID,
                    name = getString(R.string.onboarding_group_name),
                    iconKey = GroupIcon.SOCIAL.key,
                    enabled = true,
                    appIds = selectedAppIds.toSet(),
                    mindfulPauseSeconds = preset.mindfulPauseSeconds,
                    sessionDurationMinutes = preset.sessionDurationMinutes,
                    cooldownMinutes = preset.cooldownMinutes,
                    dailyQuotaMinutes = preset.dailyQuotaMinutes,
                    progressivePauseEnabled = true,
                    sharedQuota = true
                )
            )
        }
        store.saveConfig(
            config.copy(
                groups = groups,
                enabledAppIds = selectedAppIds.toSet(),
                mindfulPauseSeconds = preset.mindfulPauseSeconds,
                sessionDurationMinutes = preset.sessionDurationMinutes,
                cooldownMinutes = preset.cooldownMinutes,
                dailyQuotaMinutes = preset.dailyQuotaMinutes
            )
        )
        store.markOnboardingCompleted()
        FocusAccessibilityService.notifyConfigUpdated()
        goToDashboard()
    }

    private fun addTitle(text: String) {
        content.addView(
            Theme.text(this, text, TypeScale.TITLE_L_SP, Palette.TEXT_PRIMARY, Theme.MEDIUM).apply {
                setLineSpacing(dp(4).toFloat(), 1f)
            }
        )
    }

    private fun addBody(text: String) {
        content.addView(
            Theme.text(this, text, TypeScale.BODY_SP, Palette.TEXT_SECONDARY).apply {
                setPadding(0, dp(Space.S), 0, 0)
                setLineSpacing(dp(4).toFloat(), 1f)
            }
        )
    }

    private fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.recoverCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }.onFailure {
            Toast.makeText(this, R.string.service_test_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToDashboard() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun isAccessibilityServiceEnabledInSettings(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName)
    }

    companion object {
        private const val TOTAL_STEPS = 3
    }
}
