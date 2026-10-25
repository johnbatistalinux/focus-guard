package com.focusguard.app.ui

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.domain.FocusRewardSummary
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.matchWrap
import com.focusguard.app.ui.design.statusChip
import com.focusguard.app.ui.design.withAlpha

/**
 * Today hero: reclaimed time, resisted impulses, tangible equivalence and a
 * readable 7-day consistency strip.
 */
class ReclaimedCardView(context: Context) : LinearLayout(context) {

    private val levelChip: TextView
    private val reclaimedValue: TextView
    private val resistedView: TextView
    private val equivalenceView: TextView
    private val strip: LinearLayout

    init {
        orientation = VERTICAL
        background = Theme.box(Palette.SURFACE, Radius.CARD)
        val pad = context.dp(Space.XL)
        setPadding(pad, pad, pad, pad)
        layoutParams = matchWrap()

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            Theme.text(
                context,
                context.getString(R.string.today_reclaimed_overline).uppercase(),
                TypeScale.OVERLINE_SP,
                Palette.TEXT_MUTED,
                Theme.MEDIUM,
                TypeScale.OVERLINE_TRACKING
            ).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        levelChip = statusChip(context, "", Palette.TEXT_SECONDARY, Palette.ELEVATED)
        header.addView(levelChip)
        addView(header)

        val metricRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, context.dp(Space.M), 0, 0)
        }
        reclaimedValue = Theme.text(
            context,
            "0",
            TypeScale.DISPLAY_SP,
            Palette.SAGE,
            Theme.LIGHT
        ).apply {
            fontFeatureSettings = "tnum"
        }
        metricRow.addView(reclaimedValue)
        metricRow.addView(
            Theme.text(
                context,
                " ${context.getString(R.string.today_reclaimed_label)}",
                TypeScale.CAPTION_SP,
                Palette.TEXT_SECONDARY
            ).apply {
                setPadding(0, 0, 0, context.dp(6))
            }
        )
        addView(metricRow)

        resistedView = Theme.text(
            context,
            "",
            TypeScale.LABEL_SP,
            Palette.TEXT_PRIMARY,
            Theme.MEDIUM
        ).apply {
            setPadding(0, context.dp(Space.XS), 0, 0)
        }
        addView(resistedView)

        equivalenceView = Theme.text(
            context,
            "",
            TypeScale.CAPTION_SP,
            Palette.TEXT_MUTED
        ).apply {
            setLineSpacing(context.dp(3).toFloat(), 1f)
            setPadding(0, context.dp(Space.XS), 0, context.dp(Space.L))
        }
        addView(equivalenceView)

        strip = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(strip)
    }

    fun bind(summary: FocusRewardSummary, dayLabels: List<String>) {
        levelChip.text = summary.focusLevelTitle
        reclaimedValue.text = summary.reclaimedMinutesToday.toString()
        resistedView.text = context.resources.getQuantityString(
            R.plurals.impulses_resisted,
            summary.abortedToday,
            summary.abortedToday
        )
        equivalenceView.text = if (summary.attemptsToday == 0 && summary.totalReclaimedMinutes == 0L) {
            context.getString(R.string.today_no_activity)
        } else {
            summary.tangibleEquivalentText
        }
        renderStrip(summary, dayLabels)
    }

    private fun renderStrip(summary: FocusRewardSummary, dayLabels: List<String>) {
        strip.removeAllViews()
        val density = context.resources.displayMetrics.density
        val successDays = summary.last7DaysWithinQuota
        val trackedDays = summary.last7DaysTracked

        for (index in 0 until 7) {
            val isToday = index == 6
            val tracked = trackedDays.getOrElse(index) { false }
            val success = successDays.getOrElse(index) { false }
            val label = if (isToday) {
                context.getString(R.string.today_day_label)
            } else {
                dayLabels.getOrElse(index) { "" }
            }

            val column = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                contentDescription = when {
                    !tracked -> context.getString(R.string.today_day_untracked, label)
                    success -> context.getString(R.string.today_day_success, label)
                    else -> context.getString(R.string.today_day_miss, label)
                }
            }

            val circle = FrameLayout(context).apply {
                val size = context.dp(28)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    bottomMargin = context.dp(Space.XS)
                }
                background = when {
                    success -> Theme.box(withAlpha(Palette.SAGE, 0x2E), Radius.PILL)
                    tracked -> Theme.box(Palette.SUNKEN, Radius.PILL)
                    else -> Theme.box(Palette.SUNKEN, Radius.PILL, density = density, strokeColor = Palette.HAIRLINE, strokeWidthDp = 1)
                }
                if (isToday) {
                    background = Theme.box(
                        if (success) withAlpha(Palette.SAGE, 0x2E) else Palette.SUNKEN,
                        Radius.PILL,
                        density = density,
                        strokeColor = Palette.ACCENT,
                        strokeWidthDp = 1
                    )
                }
            }

            if (success) {
                val check = ImageView(context).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(Palette.SAGE, PorterDuff.Mode.SRC_IN)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = FrameLayout.LayoutParams(context.dp(14), context.dp(14), Gravity.CENTER)
                }
                circle.addView(check)
            }

            val labelView = Theme.text(
                context,
                label,
                TypeScale.CAPTION_SP - 1f,
                when {
                    isToday -> Palette.ACCENT
                    success -> Palette.TEXT_SECONDARY
                    else -> Palette.TEXT_MUTED
                },
                if (isToday) Theme.MEDIUM else Theme.SANS,
                gravity = Gravity.CENTER
            )
            column.addView(circle)
            column.addView(labelView)
            strip.addView(column)
        }
    }
}
