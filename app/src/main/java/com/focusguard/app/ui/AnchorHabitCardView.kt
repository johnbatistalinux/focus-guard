package com.focusguard.app.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.ui.FocusTheme.dp

/**
 * Tonal card displaying the user's selected positive anchor habit
 * with a button to open the AnchorHabitSheet.
 */
class AnchorHabitCardView(
    context: Context,
    onChangeClicked: () -> Unit
) : LinearLayout(context) {

    private val summaryTextView: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        background = FocusTheme.flatBox(FocusTheme.COLOR_SURFACE, cornerDp = 14)
        val padH = dp(16)
        val padV = dp(12)
        setPadding(padH, padV, padH, padV)

        val col = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        val label = TextView(context).apply {
            text = context.getString(R.string.anchor_habit_label)
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        }

        summaryTextView = TextView(context).apply {
            setTextColor(FocusTheme.COLOR_ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
        }
        col.addView(label)
        col.addView(summaryTextView)

        val changeBtn = Button(context).apply {
            text = context.getString(R.string.anchor_habit_action)
            isAllCaps = false
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(44)
            background = FocusTheme.flatRipple(FocusTheme.COLOR_ELEVATED, cornerDp = 10)
            setOnClickListener { onChangeClicked() }
        }

        addView(col)
        addView(changeBtn)
    }

    fun setAnchorHabit(habitText: String) {
        summaryTextView.text = habitText
    }
}
