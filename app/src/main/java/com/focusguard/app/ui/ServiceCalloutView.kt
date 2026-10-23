package com.focusguard.app.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.ui.FocusTheme.dp

/**
 * Notice card displayed when FocusGuard's Accessibility Service is inactive,
 * prompting the user with a 48dp CTA to enable it in system settings.
 */
class ServiceCalloutView(
    context: Context,
    onActivateClicked: () -> Unit
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        background = FocusTheme.flatBox(FocusTheme.COLOR_WARN_SUBTLE, cornerDp = 12)
        val pad = dp(16)
        setPadding(pad, pad, pad, pad)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.service_callout_title)
            setTextColor(FocusTheme.COLOR_WARN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(context).apply {
            text = context.getString(R.string.service_callout_subtitle)
            setTextColor(FocusTheme.COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(12))
        }

        val actionBtn = Button(context).apply {
            text = context.getString(R.string.service_callout_action)
            isAllCaps = false
            setTextColor(FocusTheme.COLOR_CANVAS)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(44)
            background = FocusTheme.flatRipple(FocusTheme.COLOR_WARN, cornerDp = 10)
            setOnClickListener { onActivateClicked() }
        }

        addView(title)
        addView(subtitle)
        addView(actionBtn)
    }
}
