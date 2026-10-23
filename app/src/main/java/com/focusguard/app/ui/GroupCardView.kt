package com.focusguard.app.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.FocusLimits
import com.focusguard.app.domain.TargetApp
import com.focusguard.app.ui.FocusTheme.performLightHaptic

/**
 * Expandable group card featuring progressive disclosure, live quota gauge,
 * status badge, rule summary, and 48dp action targets.
 */
class GroupCardView(
    context: Context,
    private val group: FocusGroup,
    private val isLocked: Boolean,
    private val onToggleEnabled: (Boolean) -> Unit,
    private val onAdjustLimitsClicked: () -> Unit,
    private val onManageAppsClicked: () -> Unit,
    private val onLockedActionAttempt: () -> Unit
) : LinearLayout(context) {

    private val quotaProgressFill: View
    private val quotaProgressEmpty: View
    private val expandableContainer: LinearLayout
    private var isExpanded: Boolean = false

    init {
        orientation = VERTICAL
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density + 0.5f).toInt()

        background = FocusTheme.flatBox(FocusTheme.COLOR_SURFACE, cornerDp = 16, density = density)
        val pad = dp(16)
        setPadding(pad, pad, pad, pad)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        }

        // Header Row: [Emoji + Name + Badges] --- [Switch]
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setOnClickListener { view ->
                isExpanded = !isExpanded
                expandableContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
                view.performLightHaptic()
            }
        }

        val titleContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(context).apply {
            text = "${group.iconEmoji} ${group.name}"
            setTextColor(if (group.enabled) FocusTheme.COLOR_TEXT_PRIMARY else FocusTheme.COLOR_TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }
        titleContainer.addView(nameView)

        if (!group.enabled) {
            val inactiveBadge = TextView(context).apply {
                text = context.getString(R.string.group_inactive_badge)
                setTextColor(FocusTheme.COLOR_TEXT_MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                background = FocusTheme.flatBox(FocusTheme.COLOR_ELEVATED, cornerDp = 6, density = density)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(8)
                }
            }
            titleContainer.addView(inactiveBadge)
        }

        val groupSwitch = Switch(context).apply {
            isChecked = group.enabled
            isEnabled = !isLocked
            alpha = if (isLocked) 0.4f else 1.0f
            setOnCheckedChangeListener { _, isChecked ->
                onToggleEnabled(isChecked)
            }
            if (isLocked) {
                setOnClickListener { onLockedActionAttempt() }
            }
        }

        headerRow.addView(titleContainer)
        headerRow.addView(groupSwitch)
        addView(headerRow)

        // Quota Text & Rule Summary
        val subtitleLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(4), 0, dp(6))
        }

        val quotaProgressTrack = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(6)).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
            background = FocusTheme.flatBox(FocusTheme.COLOR_ELEVATED, cornerDp = 3, density = density)
        }

        quotaProgressFill = View(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 0.001f)
        }
        quotaProgressEmpty = View(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        quotaProgressTrack.addView(quotaProgressFill)
        quotaProgressTrack.addView(quotaProgressEmpty)

        addView(subtitleLayout)
        addView(quotaProgressTrack)

        // Expandable Container
        expandableContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        addView(expandableContainer)
    }

    fun bindData(
        usedMinutes: Long,
        quotaMinutes: Int,
        isCooldown: Boolean,
        apps: List<TargetApp>,
        appsStats: Map<String, Pair<Long, Int>>
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density + 0.5f).toInt()

        // 1. Quota Progress Fill & Color
        val ratio = if (quotaMinutes > 0) (usedMinutes.toFloat() / quotaMinutes.toFloat()).coerceIn(0f, 1f) else 0f
        val level = FocusLimits.quotaLevel(usedMinutes, quotaMinutes)
        val fillColor = when (level) {
            FocusLimits.QuotaLevel.EXHAUSTED -> FocusTheme.COLOR_DANGER
            FocusLimits.QuotaLevel.WARNING -> FocusTheme.COLOR_WARN
            FocusLimits.QuotaLevel.OK -> FocusTheme.COLOR_ACCENT
        }

        quotaProgressFill.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, ratio.coerceAtLeast(0.001f))
        quotaProgressFill.background = FocusTheme.flatBox(fillColor, cornerDp = 3, density = density)
        quotaProgressEmpty.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, (1f - ratio).coerceAtLeast(0.001f))

        // 2. Subtitle update
        val subtitleLayout = getChildAt(1) as LinearLayout
        subtitleLayout.removeAllViews()

        val progressRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val progressText = TextView(context).apply {
            text = "$usedMinutes de $quotaMinutes min hoje"
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        progressRow.addView(progressText)

        if (isCooldown) {
            val cooldownBadge = TextView(context).apply {
                text = context.getString(R.string.group_cooldown_badge)
                setTextColor(FocusTheme.COLOR_DANGER)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                background = FocusTheme.flatBox(FocusTheme.COLOR_DANGER_SUBTLE, cornerDp = 6, density = density)
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }
            progressRow.addView(cooldownBadge)
        } else if (level == FocusLimits.QuotaLevel.EXHAUSTED) {
            val exhaustedBadge = TextView(context).apply {
                text = context.getString(R.string.group_quota_exhausted_badge)
                setTextColor(FocusTheme.COLOR_WARN)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                background = FocusTheme.flatBox(FocusTheme.COLOR_WARN_SUBTLE, cornerDp = 6, density = density)
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }
            progressRow.addView(exhaustedBadge)
        }
        subtitleLayout.addView(progressRow)

        val ruleText = TextView(context).apply {
            text = "${group.sessionDurationMinutes} min por sessão · pausa de ${group.mindfulPauseSeconds} s · espera de ${group.cooldownMinutes} min"
            setTextColor(FocusTheme.COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, 0)
        }
        subtitleLayout.addView(ruleText)

        // 3. Populate Expandable Apps & Action Buttons
        expandableContainer.removeAllViews()
        if (apps.isEmpty()) {
            val emptyPrompt = TextView(context).apply {
                text = context.getString(R.string.group_empty_prompt)
                setTextColor(FocusTheme.COLOR_TEXT_MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(12))
            }
            expandableContainer.addView(emptyPrompt)
        } else {
            for (app in apps) {
                val stats = appsStats[app.id] ?: Pair(0L, 0)
                val appRow = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(40)
                    setPadding(0, dp(4), 0, dp(4))
                }

                val brandDot = View(context).apply {
                    val size = dp(8)
                    layoutParams = LayoutParams(size, size).apply {
                        marginEnd = dp(10)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(android.graphics.Color.parseColor(app.brandColorHex))
                    }
                }

                val nameCol = TextView(context).apply {
                    text = app.displayName
                    setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                }

                val statsCol = TextView(context).apply {
                    text = "${stats.first} min · ${stats.second} sessão(ões)"
                    setTextColor(FocusTheme.COLOR_TEXT_MUTED)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                }

                appRow.addView(brandDot)
                appRow.addView(nameCol)
                appRow.addView(statsCol)
                expandableContainer.addView(appRow)
            }
        }

        // Action Buttons Row (Ajustar Limites + Gerenciar Aplicativos)
        val actionsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }

        val adjustBtn = Button(context).apply {
            text = context.getString(R.string.group_adjust_limits)
            isAllCaps = false
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(48)
            background = FocusTheme.flatRipple(FocusTheme.COLOR_ELEVATED, cornerDp = 10, density = density)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                if (isLocked) onLockedActionAttempt() else onAdjustLimitsClicked()
            }
        }

        val appsBtn = Button(context).apply {
            text = context.getString(R.string.group_add_apps)
            isAllCaps = false
            setTextColor(FocusTheme.COLOR_ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(48)
            background = FocusTheme.flatRipple(FocusTheme.COLOR_ELEVATED, cornerDp = 10, density = density)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener {
                if (isLocked) onLockedActionAttempt() else onManageAppsClicked()
            }
        }

        actionsRow.addView(adjustBtn)
        actionsRow.addView(appsBtn)
        expandableContainer.addView(actionsRow)
    }
}
