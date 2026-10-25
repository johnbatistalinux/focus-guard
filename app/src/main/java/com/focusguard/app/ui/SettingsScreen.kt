package com.focusguard.app.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import com.focusguard.app.R
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.matchWrap
import com.focusguard.app.ui.design.sectionHeader
import com.focusguard.app.ui.presenter.SettingsState
import com.focusguard.app.ui.design.hapticLight

/**
 * "Ajustes" tab: service activation with a testable barrier preview, the anchor
 * habit, reversible data resets and a zero-telemetry statement.
 */
class SettingsScreen(
    context: Context,
    private val onOpenAccessibilitySettings: () -> Unit,
    private val onTestBarrier: () -> Unit,
    private val onChangeAnchorHabit: () -> Unit,
    private val onResetToday: () -> Unit,
    private val onResetAll: () -> Unit,
    private val onLockedAttempt: () -> Unit
) : ScrollView(context) {

    private val content: LinearLayout
    private val serviceCard: ServiceStatusCard
    private val anchorCard: AnchorHabitCardView
    private val resetTodayRow: LinearLayout
    private val resetAllRow: LinearLayout
    private var isLockedNow: Boolean = false

    init {
        setBackgroundColor(Palette.CANVAS)
        isFillViewport = true
        isVerticalScrollBarEnabled = false

        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(Space.XL), context.dp(Space.L), context.dp(Space.XL), context.dp(Space.HUGE))
        }
        Theme.applyInsets(content, applyTop = true, applyBottom = false)

        content.addView(
            Theme.text(
                context,
                context.getString(R.string.settings_title),
                TypeScale.DISPLAY_SP,
                Palette.TEXT_PRIMARY,
                Theme.LIGHT
            ).apply {
                setPadding(0, context.dp(Space.S), 0, context.dp(Space.M))
            }
        )

        content.addView(sectionHeader(context, context.getString(R.string.settings_service_title)))
        serviceCard = ServiceStatusCard(
            context,
            detailed = true,
            onActivate = onOpenAccessibilitySettings,
            onTest = onTestBarrier
        ).apply {
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.M) }
        }
        content.addView(serviceCard)

        content.addView(sectionHeader(context, context.getString(R.string.settings_habit_title)))
        anchorCard = AnchorHabitCardView(context) {
            if (isLockedNow) onLockedAttempt() else onChangeAnchorHabit()
        }.apply {
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.M) }
        }
        content.addView(anchorCard)
        content.addView(
            Theme.text(
                context,
                context.getString(R.string.settings_habit_body),
                TypeScale.CAPTION_SP,
                Palette.TEXT_MUTED
            ).apply {
                setPadding(0, 0, 0, context.dp(Space.S))
            }
        )

        content.addView(sectionHeader(context, context.getString(R.string.settings_data_title)))
        resetTodayRow = actionRow(
            context.getString(R.string.settings_reset_today),
            context.getString(R.string.settings_reset_today_body)
        ) {
            if (isLockedNow) onLockedAttempt() else onResetToday()
        }
        content.addView(resetTodayRow)
        resetAllRow = actionRow(
            context.getString(R.string.settings_reset_all),
            context.getString(R.string.settings_reset_all_body),
            danger = true
        ) {
            if (isLockedNow) onLockedAttempt() else onResetAll()
        }
        content.addView(resetAllRow)

        content.addView(sectionHeader(context, context.getString(R.string.settings_about_title)))
        val aboutCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Theme.box(Palette.SURFACE, Radius.CARD)
            val pad = context.dp(Space.L)
            setPadding(pad, pad, pad, pad)
            layoutParams = matchWrap()
        }
        aboutCard.addView(
            Theme.text(
                context,
                context.getString(R.string.settings_about_body),
                TypeScale.BODY_SP,
                Palette.TEXT_SECONDARY
            ).apply {
                setLineSpacing(context.dp(3).toFloat(), 1f)
            }
        )
        aboutCard.addView(
            Theme.text(
                context,
                context.getString(R.string.settings_privacy),
                TypeScale.CAPTION_SP,
                Palette.TEXT_MUTED
            ).apply {
                setPadding(0, context.dp(Space.S), 0, 0)
                setLineSpacing(context.dp(3).toFloat(), 1f)
            }
        )
        aboutCard.addView(
            Theme.text(
                context,
                context.getString(R.string.settings_version, appVersionName()),
                TypeScale.CAPTION_SP,
                Palette.TEXT_MUTED,
                Theme.MEDIUM
            ).apply {
                setPadding(0, context.dp(Space.M), 0, 0)
            }
        )
        content.addView(aboutCard)

        addView(content)
    }

    fun bind(state: SettingsState) {
        isLockedNow = state.lock.isLocked
        serviceCard.bind(state.serviceActive)
        anchorCard.setAnchorHabit(state.config.selectedAnchorHabit)
        anchorCard.alpha = if (isLockedNow) 0.5f else 1f
        resetTodayRow.alpha = if (isLockedNow) 0.5f else 1f
        resetAllRow.alpha = if (isLockedNow) 0.5f else 1f
    }

    private fun actionRow(
        title: String,
        body: String,
        danger: Boolean = false,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = Theme.ripple(Palette.SURFACE, Radius.CARD)
        val pad = context.dp(Space.L)
        setPadding(pad, pad, pad, pad)
        isClickable = true
        isFocusable = true
        layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.S) }
        addView(
            Theme.text(
                context,
                title,
                TypeScale.BODY_SP,
                if (danger) Palette.DANGER else Palette.TEXT_PRIMARY,
                Theme.MEDIUM
            )
        )
        addView(
            Theme.text(
                context,
                body,
                TypeScale.CAPTION_SP,
                Palette.TEXT_MUTED
            ).apply {
                setPadding(0, context.dp(2), 0, 0)
                setLineSpacing(context.dp(2).toFloat(), 1f)
            }
        )
        setOnClickListener { view ->
            view.hapticLight()
            onClick()
        }
    }

    private fun appVersionName(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty()
    }.getOrDefault("")
}
