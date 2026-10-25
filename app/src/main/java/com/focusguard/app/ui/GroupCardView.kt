package com.focusguard.app.ui

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.domain.FocusLimits
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.ui.design.ButtonStyle
import com.focusguard.app.ui.design.Motion
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.QuotaBarView
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.button
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.matchWrap
import com.focusguard.app.ui.design.monogram
import com.focusguard.app.ui.design.statusChip
import com.focusguard.app.ui.design.themedSwitch
import com.focusguard.app.ui.presenter.GroupLimitState
import com.focusguard.app.ui.design.hapticLight

/**
 * Group card v2: isolated switch, readable quota with numbers, explicit expand
 * affordance and in-place updates that preserve expansion state.
 */
class GroupCardView(
    context: Context,
    private val group: FocusGroup,
    private val onToggleEnabled: (Boolean) -> Unit,
    private val onEditClicked: () -> Unit,
    private val onManageAppsClicked: () -> Unit,
    private val onLockedActionAttempt: () -> Unit
) : LinearLayout(context) {

    private val iconBadge: FrameLayout
    private val iconImage: ImageView
    private val nameView: TextView
    private val metaView: TextView
    private val toggle: Switch
    private val quotaText: TextView
    private val statusChipView: TextView
    private val quotaBar: QuotaBarView
    private val expandLabel: TextView
    private val chevron: ImageView
    private val expandedContainer: LinearLayout
    private val appsContainer: LinearLayout
    private val editButton: android.widget.Button
    private val appsButton: android.widget.Button

    private var isExpanded: Boolean = false
    private var isLocked: Boolean = false
    private var appCount: Int = group.appIds.size

    val groupId: String get() = group.id

    init {
        orientation = VERTICAL
        background = Theme.box(Palette.SURFACE, Radius.CARD)
        val pad = context.dp(Space.L)
        setPadding(pad, pad, pad, context.dp(Space.S))
        layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.M) }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        iconBadge = FrameLayout(context).apply {
            val size = context.dp(40)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = context.dp(Space.M)
            }
            background = Theme.box(Palette.ACCENT_SUBTLE, Radius.PILL)
        }
        iconImage = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(context.dp(22), context.dp(22), Gravity.CENTER)
            setColorFilter(Palette.ACCENT, PorterDuff.Mode.SRC_IN)
        }
        iconBadge.addView(iconImage)

        val textCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameView = Theme.text(context, group.name, TypeScale.TITLE_M_SP, Palette.TEXT_PRIMARY, Theme.MEDIUM)
        metaView = Theme.text(context, "", TypeScale.CAPTION_SP, Palette.TEXT_MUTED).apply {
            setPadding(0, context.dp(2), 0, 0)
        }
        textCol.addView(nameView)
        textCol.addView(metaView)

        toggle = themedSwitch(context, group.enabled).apply {
            contentDescription = context.getString(R.string.group_toggle_desc, group.name)
        }
        toggle.setOnCheckedChangeListener { buttonView, checked ->
            if (isLocked) {
                buttonView.isChecked = !checked
                onLockedActionAttempt()
            } else {
                onToggleEnabled(checked)
            }
        }

        header.addView(iconBadge)
        header.addView(textCol)
        header.addView(toggle)
        addView(header)

        val quotaHeader = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(Space.M), 0, 0)
        }
        quotaText = Theme.text(context, "", TypeScale.LABEL_SP, Palette.TEXT_SECONDARY, Theme.MEDIUM)
            .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        statusChipView = statusChip(context, "", Palette.TEXT_MUTED, Palette.ELEVATED)
        quotaHeader.addView(quotaText)
        quotaHeader.addView(statusChipView)
        addView(quotaHeader)

        quotaBar = QuotaBarView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dp(8)
            ).apply { topMargin = context.dp(Space.S) }
        }
        addView(quotaBar)

        val dividerView = View(context).apply {
            setBackgroundColor(Palette.HAIRLINE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dp(1)
            ).apply { topMargin = context.dp(Space.M) }
        }
        addView(dividerView)

        val expandRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(48)
            isClickable = true
            isFocusable = true
            setOnClickListener { view ->
                view.hapticLight()
                toggleExpanded()
            }
        }
        expandLabel = Theme.text(
            context,
            context.getString(R.string.group_view_apps, appCount),
            TypeScale.LABEL_SP,
            Palette.ACCENT,
            Theme.MEDIUM
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        chevron = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_down)
            setColorFilter(Palette.ACCENT, PorterDuff.Mode.SRC_IN)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(context.dp(18), context.dp(18))
        }
        expandRow.addView(expandLabel)
        expandRow.addView(chevron)
        addView(expandRow)

        expandedContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            setPadding(0, 0, 0, context.dp(Space.S))
        }
        appsContainer = LinearLayout(context).apply { orientation = VERTICAL }
        expandedContainer.addView(appsContainer)

        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, context.dp(Space.S), 0, 0)
        }
        editButton = button(context, context.getString(R.string.group_edit), ButtonStyle.SECONDARY) {
            if (isLocked) onLockedActionAttempt() else onEditClicked()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = context.dp(Space.XS)
            }
        }
        appsButton = button(context, context.getString(R.string.group_manage_apps), ButtonStyle.GHOST) {
            if (isLocked) onLockedActionAttempt() else onManageAppsClicked()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = context.dp(Space.XS)
            }
        }
        actions.addView(editButton)
        actions.addView(appsButton)
        expandedContainer.addView(actions)
        addView(expandedContainer)
    }

    fun bindData(state: GroupLimitState, locked: Boolean) {
        isLocked = locked
        appCount = state.apps.size
        iconImage.setImageResource(state.group.icon.drawableRes())
        nameView.text = state.group.name
        nameView.setTextColor(if (state.group.enabled) Palette.TEXT_PRIMARY else Palette.TEXT_MUTED)
        metaView.text = buildString {
            append(
                context.resources.getQuantityString(
                    R.plurals.apps_count,
                    appCount,
                    appCount
                )
            )
            append(" · ")
            append(context.getString(R.string.group_session_rule, state.group.sessionDurationMinutes))
        }
        metaView.setTextColor(Palette.TEXT_MUTED)

        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = state.group.enabled
        toggle.alpha = if (locked) 0.45f else 1f
        toggle.setOnCheckedChangeListener { buttonView, checked ->
            if (isLocked) {
                buttonView.isChecked = !checked
                onLockedActionAttempt()
            } else {
                onToggleEnabled(checked)
            }
        }

        val ratio = if (state.quotaMinutes > 0) {
            state.usedMinutes.toFloat() / state.quotaMinutes.toFloat()
        } else {
            0f
        }
        val barColor = when {
            state.isCooldown -> Palette.DANGER
            state.level == FocusLimits.QuotaLevel.EXHAUSTED -> Palette.DANGER
            state.level == FocusLimits.QuotaLevel.WARNING -> Palette.WARN
            else -> Palette.ACCENT
        }
        quotaBar.setProgress(ratio, barColor)
        quotaText.text = context.getString(
            R.string.group_used_of_quota,
            state.usedMinutes,
            state.quotaMinutes
        )

        when {
            !state.group.enabled -> {
                statusChipView.text = context.getString(R.string.group_inactive_chip)
                statusChipView.setTextColor(Palette.TEXT_MUTED)
                statusChipView.background = Theme.box(Palette.ELEVATED, Radius.PILL)
            }
            state.isCooldown -> {
                statusChipView.text = context.getString(R.string.group_cooldown_chip)
                statusChipView.setTextColor(Palette.DANGER)
                statusChipView.background = Theme.box(Palette.DANGER_SUBTLE, Radius.PILL)
            }
            state.level == FocusLimits.QuotaLevel.EXHAUSTED -> {
                statusChipView.text = context.getString(R.string.group_quota_exhausted)
                statusChipView.setTextColor(Palette.WARN)
                statusChipView.background = Theme.box(Palette.WARN_SUBTLE, Radius.PILL)
            }
            else -> {
                statusChipView.text = context.resources.getQuantityString(
                    R.plurals.minutes_remaining,
                    state.remainingMinutes.toInt(),
                    state.remainingMinutes.toInt()
                )
                statusChipView.setTextColor(Palette.SAGE)
                statusChipView.background = Theme.box(Palette.SAGE_SUBTLE, Radius.PILL)
            }
        }

        renderApps(state)
        expandLabel.text = if (isExpanded) {
            context.getString(R.string.group_hide_apps)
        } else {
            context.getString(R.string.group_view_apps, appCount)
        }
        editButton.alpha = if (locked) 0.5f else 1f
        appsButton.alpha = if (locked) 0.5f else 1f
    }

    private fun renderApps(state: GroupLimitState) {
        appsContainer.removeAllViews()
        if (state.apps.isEmpty()) {
            appsContainer.addView(
                Theme.text(
                    context,
                    context.getString(R.string.group_empty_apps),
                    TypeScale.CAPTION_SP,
                    Palette.TEXT_MUTED
                ).apply {
                    setPadding(0, context.dp(Space.S), 0, context.dp(Space.S))
                }
            )
            return
        }
        for (row in state.apps) {
            val appRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = context.dp(48)
            }
            appRow.addView(
                monogram(context, row.app.displayName, row.app.brandColorHex, sizeDp = 32).apply {
                    layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                        marginEnd = context.dp(Space.M)
                    }
                }
            )
            appRow.addView(
                Theme.text(
                    context,
                    row.app.displayName,
                    TypeScale.BODY_SP,
                    Palette.TEXT_PRIMARY
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            val usageText = if (row.usedMinutes == 0L && row.sessions == 0) {
                context.getString(R.string.group_app_usage_empty)
            } else {
                context.getString(R.string.group_app_usage, row.usedMinutes, row.sessions)
            }
            appRow.addView(
                Theme.text(
                    context,
                    usageText,
                    TypeScale.CAPTION_SP,
                    Palette.TEXT_MUTED
                )
            )
            appsContainer.addView(appRow)
        }
    }

    fun expand() {
        if (!isExpanded) toggleExpanded()
    }

    fun isExpanded(): Boolean = isExpanded

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        expandedContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        expandLabel.text = if (isExpanded) {
            context.getString(R.string.group_hide_apps)
        } else {
            context.getString(R.string.group_view_apps, appCount)
        }
        chevron.animate()
            .rotation(if (isExpanded) 180f else 0f)
            .setDuration(Motion.FAST)
            .start()
    }
}
