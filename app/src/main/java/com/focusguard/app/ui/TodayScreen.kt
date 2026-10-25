package com.focusguard.app.ui

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.domain.FocusLimits
import com.focusguard.app.ui.design.ButtonStyle
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.QuotaBarView
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.button
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.emptyState
import com.focusguard.app.ui.design.formatClock
import com.focusguard.app.ui.design.matchWrap
import com.focusguard.app.ui.design.sectionHeader
import com.focusguard.app.ui.design.statusChip
import com.focusguard.app.ui.presenter.TodayState
import com.focusguard.app.ui.design.hapticLight

/**
 * "Hoje" tab: a calm daily briefing. Reclaimed time, resisted impulses, an
 * optional live session, a compact service callout and a per-group limit preview.
 */
class TodayScreen(
    context: Context,
    private val onOpenAccessibilitySettings: () -> Unit,
    private val onTestBarrier: () -> Unit,
    private val onViewGroups: () -> Unit,
    private val onCreateGroup: () -> Unit,
    private val onEndSession: () -> Unit
) : ScrollView(context) {

    private val content: LinearLayout
    private val serviceCard: ServiceStatusCard
    private val lockBanner: TextView
    private val reclaimedCard: ReclaimedCardView
    private val sessionCard: LinearLayout
    private val sessionTitle: TextView
    private val sessionBody: TextView
    private val limitsHeader: LinearLayout
    private val limitsContainer: LinearLayout
    private val emptyGroups: LinearLayout

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
                context.getString(R.string.today_title),
                TypeScale.DISPLAY_SP,
                Palette.TEXT_PRIMARY,
                Theme.LIGHT
            ).apply {
                setPadding(0, context.dp(Space.S), 0, context.dp(Space.XS))
            }
        )

        serviceCard = ServiceStatusCard(
            context,
            detailed = false,
            onActivate = onOpenAccessibilitySettings,
            onTest = onTestBarrier
        ).apply {
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.M) }
        }
        content.addView(serviceCard)

        lockBanner = Theme.text(
            context,
            "",
            TypeScale.CAPTION_SP,
            Palette.DANGER,
            Theme.MEDIUM
        ).apply {
            visibility = View.GONE
            background = Theme.box(Palette.DANGER_SUBTLE, Radius.SMALL)
            val pad = context.dp(Space.M)
            setPadding(pad, pad, pad, pad)
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.M) }
        }
        content.addView(lockBanner)

        reclaimedCard = ReclaimedCardView(context).apply {
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.M) }
        }
        content.addView(reclaimedCard)

        sessionCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Theme.box(Palette.ACCENT_SUBTLE, Radius.CARD)
            val pad = context.dp(Space.L)
            setPadding(pad, pad, pad, pad)
            visibility = View.GONE
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.M) }
        }
        val sessionHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sessionTitle = Theme.text(
            context,
            context.getString(R.string.today_session_active),
            TypeScale.LABEL_SP,
            Palette.ACCENT,
            Theme.MEDIUM
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sessionHeader.addView(sessionTitle)
        sessionCard.addView(sessionHeader)
        sessionBody = Theme.text(context, "", TypeScale.TITLE_M_SP, Palette.TEXT_PRIMARY, Theme.MEDIUM).apply {
            setPadding(0, context.dp(Space.XS), 0, context.dp(Space.M))
        }
        sessionCard.addView(sessionBody)
        sessionCard.addView(
            button(context, context.getString(R.string.today_session_end), ButtonStyle.SECONDARY) {
                onEndSession()
            }
        )
        content.addView(sessionCard)

        limitsHeader = sectionHeader(
            context,
            context.getString(R.string.today_limits_title),
            context.getString(R.string.today_view_groups)
        ) { onViewGroups() }
        content.addView(limitsHeader)

        limitsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(limitsContainer)

        emptyGroups = emptyState(
            context,
            R.drawable.ic_groups,
            context.getString(R.string.today_no_groups_title),
            context.getString(R.string.today_no_groups_body),
            context.getString(R.string.today_no_groups_action)
        ) { onCreateGroup() }
        emptyGroups.visibility = View.GONE
        content.addView(emptyGroups)

        addView(content)
    }

    fun bind(state: TodayState, dayLabels: List<String>) {
        val savedScroll = scrollY
        serviceCard.visibility = if (state.serviceActive) View.GONE else View.VISIBLE
        serviceCard.bind(state.serviceActive)

        if (state.lock.isLocked) {
            lockBanner.visibility = View.VISIBLE
            lockBanner.text = state.lock.reasonMessage ?: context.getString(R.string.lock_banner_title)
        } else {
            lockBanner.visibility = View.GONE
        }

        reclaimedCard.bind(state.summary, dayLabels)

        val session = state.activeSession
        if (session != null) {
            sessionCard.visibility = View.VISIBLE
            sessionBody.text = context.getString(
                R.string.today_session_remaining,
                formatClock(session.remainingSeconds),
                session.displayName
            )
        } else {
            sessionCard.visibility = View.GONE
        }

        renderLimits(state)
        post { scrollTo(0, savedScroll) }
    }

    private fun renderLimits(state: TodayState) {
        limitsContainer.removeAllViews()
        val hasGroups = state.groups.isNotEmpty()
        limitsContainer.visibility = if (hasGroups) View.VISIBLE else View.GONE
        emptyGroups.visibility = if (hasGroups) View.GONE else View.VISIBLE
        limitsHeader.visibility = if (hasGroups) View.VISIBLE else View.GONE

        for (groupState in state.groups) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = Theme.ripple(Palette.SURFACE, Radius.CARD)
                val pad = context.dp(Space.M)
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                setOnClickListener { view ->
                    view.hapticLight()
                    onViewGroups()
                }
                layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.S) }
            }

            val top = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val icon = ImageView(context).apply {
                setImageResource(groupState.group.icon.drawableRes())
                setColorFilter(
                    if (groupState.group.enabled) Palette.ACCENT else Palette.TEXT_MUTED,
                    PorterDuff.Mode.SRC_IN
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(context.dp(20), context.dp(20)).apply {
                    marginEnd = context.dp(Space.S)
                }
            }
            top.addView(icon)
            top.addView(
                Theme.text(
                    context,
                    groupState.group.name,
                    TypeScale.BODY_SP,
                    if (groupState.group.enabled) Palette.TEXT_PRIMARY else Palette.TEXT_MUTED,
                    Theme.MEDIUM
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            val chip = statusChip(
                context,
                limitChipLabel(groupState.level, groupState.isCooldown, groupState.remainingMinutes),
                limitChipColor(groupState.level, groupState.isCooldown),
                limitChipFill(groupState.level, groupState.isCooldown)
            )
            top.addView(chip)
            row.addView(top)

            val bar = QuotaBarView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.dp(6)
                ).apply { topMargin = context.dp(Space.S) }
            }
            val ratio = if (groupState.quotaMinutes > 0) {
                groupState.usedMinutes.toFloat() / groupState.quotaMinutes.toFloat()
            } else {
                0f
            }
            bar.setProgress(ratio, limitBarColor(groupState.level, groupState.isCooldown))
            row.addView(bar)
            limitsContainer.addView(row)
        }
    }

    private fun limitChipLabel(
        level: FocusLimits.QuotaLevel,
        isCooldown: Boolean,
        remainingMinutes: Long
    ): String = when {
        isCooldown -> context.getString(R.string.group_cooldown_chip)
        level == FocusLimits.QuotaLevel.EXHAUSTED -> context.getString(R.string.group_quota_exhausted)
        else -> context.resources.getQuantityString(
            R.plurals.minutes_remaining,
            remainingMinutes.toInt(),
            remainingMinutes.toInt()
        )
    }

    private fun limitChipColor(level: FocusLimits.QuotaLevel, isCooldown: Boolean): Int = when {
        isCooldown || level == FocusLimits.QuotaLevel.EXHAUSTED -> Palette.DANGER
        level == FocusLimits.QuotaLevel.WARNING -> Palette.WARN
        else -> Palette.SAGE
    }

    private fun limitChipFill(level: FocusLimits.QuotaLevel, isCooldown: Boolean): Int = when {
        isCooldown || level == FocusLimits.QuotaLevel.EXHAUSTED -> Palette.DANGER_SUBTLE
        level == FocusLimits.QuotaLevel.WARNING -> Palette.WARN_SUBTLE
        else -> Palette.SAGE_SUBTLE
    }

    private fun limitBarColor(level: FocusLimits.QuotaLevel, isCooldown: Boolean): Int = when {
        isCooldown || level == FocusLimits.QuotaLevel.EXHAUSTED -> Palette.DANGER
        level == FocusLimits.QuotaLevel.WARNING -> Palette.WARN
        else -> Palette.ACCENT
    }
}
