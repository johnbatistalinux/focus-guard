package com.focusguard.app.service

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.formatClock
import com.focusguard.app.ui.design.hapticConfirm
import com.focusguard.app.ui.design.hapticLight

/**
 * Floating session pill: monospaced countdown, a thin session-progress track,
 * tap to swap sides and long-press to end the session early.
 */
class FloatingPillView(
    context: Context,
    private val onToggleSide: () -> Unit,
    private val onEndSession: () -> Unit
) : LinearLayout(context) {

    private val timerText: TextView
    private val progressFill: View
    private var lastStateKey: Int = -1
    private var isEndingSoonNow: Boolean = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = Theme.box(Palette.SURFACE, Radius.PILL, strokeColor = Palette.HAIRLINE, strokeWidthDp = 1)
        val padH = context.dp(Space.M)
        val padV = context.dp(Space.S)
        setPadding(padH, padV, padH, padV)
        isClickable = true
        isFocusable = true

        timerText = Theme.text(
            context,
            "",
            TypeScale.LABEL_SP + 1f,
            Palette.TEXT_PRIMARY,
            Theme.MEDIUM,
            gravity = Gravity.CENTER
        ).apply {
            fontFeatureSettings = "tnum"
        }
        addView(timerText)

        val track = LinearLayout(context).apply {
            orientation = HORIZONTAL
            background = Theme.box(Palette.ELEVATED, Radius.PILL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dp(3)
            ).apply { topMargin = context.dp(Space.XS) }
        }
        progressFill = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            background = Theme.box(Palette.ACCENT, Radius.PILL)
        }
        track.addView(progressFill)
        addView(track)

        setOnClickListener { view ->
            if (isEndingSoonNow) {
                view.hapticConfirm()
                onEndSession()
            } else {
                view.hapticLight()
                onToggleSide()
            }
        }
        setOnLongClickListener { view ->
            view.hapticConfirm()
            onEndSession()
            true
        }
    }

    fun bind(remainingSeconds: Long, totalSeconds: Long) {
        val safeRemaining = remainingSeconds.coerceAtLeast(0L)
        val isEndingSoon = safeRemaining <= 30L
        val isUrgent = safeRemaining <= 10L
        val clockStr = formatClock(safeRemaining)

        isEndingSoonNow = isEndingSoon
        if (isEndingSoon) {
            timerText.text = context.getString(R.string.overlay_pill_ending_soon, clockStr)
            contentDescription = context.getString(R.string.overlay_pill_ending_desc, clockStr)
        } else {
            timerText.text = clockStr
            contentDescription = context.getString(R.string.overlay_pill_desc, clockStr)
        }

        val stateKey = when {
            isUrgent -> 2
            isEndingSoon -> 1
            else -> 0
        }
        if (stateKey != lastStateKey) {
            val enteredEndingWindow = lastStateKey == 0 && stateKey >= 1
            lastStateKey = stateKey
            val (fill, textColor, barColor) = when (stateKey) {
                2 -> Triple(Palette.DANGER_SUBTLE, Palette.DANGER, Palette.DANGER)
                1 -> Triple(Palette.WARN_SUBTLE, Palette.WARN, Palette.WARN)
                else -> Triple(Palette.SURFACE, Palette.TEXT_PRIMARY, Palette.ACCENT)
            }
            background = Theme.box(fill, Radius.PILL, strokeColor = Palette.HAIRLINE, strokeWidthDp = 1)
            timerText.setTextColor(textColor)
            progressFill.background = Theme.box(barColor, Radius.PILL)
            if (enteredEndingWindow) {
                hapticLight()
            }
        }

        val ratio = if (totalSeconds > 0L) {
            (safeRemaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
        progressFill.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            ratio.coerceAtLeast(0.001f)
        )
    }
}
