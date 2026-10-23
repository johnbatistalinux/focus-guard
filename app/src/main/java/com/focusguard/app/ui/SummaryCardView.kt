package com.focusguard.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.domain.FocusRewardSummary

/**
 * Compact flat daily summary card displaying mastery percentage, reclaimed time,
 * 7-day consistency dots, and the focus level badge. Zero-bloat native views.
 */
class SummaryCardView(context: Context) : LinearLayout(context) {

    private val levelBadgeText: TextView
    private val masteryValueText: TextView
    private val reclaimedValueText: TextView
    private val consistencyCaptionText: TextView
    private val dotsRowLayout: LinearLayout

    init {
        orientation = VERTICAL
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density + 0.5f).toInt()

        background = FocusTheme.flatBox(FocusTheme.COLOR_SURFACE, cornerDp = 16, density = density)
        val pad = dp(16)
        setPadding(pad, pad, pad, pad)

        // 1. Header row: "RESUMO DO DIA" and level badge
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        val sectionTitle = TextView(context).apply {
            text = context.getString(R.string.summary_title).uppercase()
            setTextColor(FocusTheme.COLOR_TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        levelBadgeText = TextView(context).apply {
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = FocusTheme.flatBox(FocusTheme.COLOR_ELEVATED, cornerDp = 8, density = density)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        headerRow.addView(sectionTitle)
        headerRow.addView(levelBadgeText)
        addView(headerRow)

        // 2. Metrics row: Mastery % (left) and Reclaimed Time (right)
        val metricsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }

        val leftMetricCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        masteryValueText = TextView(context).apply {
            setTextColor(FocusTheme.COLOR_ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val masteryLabel = TextView(context).apply {
            text = context.getString(R.string.summary_mastery_label)
            setTextColor(FocusTheme.COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        leftMetricCol.addView(masteryValueText)
        leftMetricCol.addView(masteryLabel)

        val rightMetricCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        reclaimedValueText = TextView(context).apply {
            setTextColor(FocusTheme.COLOR_ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val reclaimedLabel = TextView(context).apply {
            text = context.getString(R.string.summary_reclaimed_label)
            setTextColor(FocusTheme.COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        rightMetricCol.addView(reclaimedValueText)
        rightMetricCol.addView(reclaimedLabel)

        metricsRow.addView(leftMetricCol)
        metricsRow.addView(rightMetricCol)
        addView(metricsRow)

        // 3. 7-Day Consistency strip
        dotsRowLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
        }
        addView(dotsRowLayout)

        consistencyCaptionText = TextView(context).apply {
            setTextColor(FocusTheme.COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        addView(consistencyCaptionText)
    }

    fun bind(summary: FocusRewardSummary) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density + 0.5f).toInt()

        levelBadgeText.text = summary.focusLevelTitle
        masteryValueText.text = "${summary.masteryPercentToday}%"
        reclaimedValueText.text = "+${summary.reclaimedMinutesToday} min"
        consistencyCaptionText.text = summary.consistencySummaryText

        // Render 7-day dots strip
        dotsRowLayout.removeAllViews()
        val cleanDots = summary.last7DaysDots.filter { it == '●' || it == '○' || it == '·' }
        val dayLabels = listOf("D-6", "D-5", "D-4", "D-3", "D-2", "Ontem", "Hoje")

        for (i in 0 until 7) {
            val isSuccess = if (i < cleanDots.length) cleanDots[i] == '●' else false
            val isToday = (i == 6)

            val pillCol = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }

            val dotView = View(context).apply {
                val size = dp(if (isToday) 11 else 8)
                layoutParams = LayoutParams(size, size).apply {
                    bottomMargin = dp(3)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    val color = when {
                        isSuccess -> FocusTheme.COLOR_ACCENT
                        isToday -> FocusTheme.COLOR_ACCENT
                        else -> FocusTheme.COLOR_TEXT_MUTED
                    }
                    setColor(color)
                    if (isToday) {
                        setStroke(dp(1), Color.WHITE)
                    }
                }
            }

            val labelView = TextView(context).apply {
                text = dayLabels[i]
                setTextColor(if (isToday) FocusTheme.COLOR_TEXT_PRIMARY else FocusTheme.COLOR_TEXT_MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
            }

            pillCol.addView(dotView)
            pillCol.addView(labelView)
            dotsRowLayout.addView(pillCol)
        }
    }
}
