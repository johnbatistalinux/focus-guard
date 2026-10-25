package com.focusguard.app.ui.design

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.widget.TextView

/**
 * Warm Dusk design tokens: a calm, warm dark palette with a single amber accent,
 * a restrained type scale, an 8pt spacing grid, and native-only primitives.
 */
object Palette {
    const val CANVAS: Int = 0xFF12100E.toInt()
    const val SURFACE: Int = 0xFF1C1815.toInt()
    const val ELEVATED: Int = 0xFF27211C.toInt()
    const val SUNKEN: Int = 0xFF171310.toInt()
    const val HAIRLINE: Int = 0xFF3A322B.toInt()
    const val OVERLAY_BG: Int = 0xF712100E.toInt()

    const val TEXT_PRIMARY: Int = 0xFFF5EEE6.toInt()
    const val TEXT_SECONDARY: Int = 0xFFC9BDB1.toInt()
    const val TEXT_MUTED: Int = 0xFF9C8F83.toInt()

    const val ACCENT: Int = 0xFFE9B872.toInt()
    const val ACCENT_ON: Int = 0xFF2A1C0C.toInt()
    const val ACCENT_SUBTLE: Int = 0x1FE9B872.toInt()
    const val ACCENT_TRACK: Int = 0x55E9B872.toInt()

    const val SAGE: Int = 0xFF9CC5A1.toInt()
    const val SAGE_SUBTLE: Int = 0x1F9CC5A1.toInt()

    const val WARN: Int = 0xFFE08A4C.toInt()
    const val WARN_SUBTLE: Int = 0x1FE08A4C.toInt()

    const val DANGER: Int = 0xFFD9635A.toInt()
    const val DANGER_SUBTLE: Int = 0x1FD9635A.toInt()

    const val RIPPLE: Int = 0x22FFFFFF.toInt()
}

object TypeScale {
    const val DISPLAY_SP = 34f
    const val TIMER_SP = 40f
    const val TITLE_L_SP = 22f
    const val TITLE_M_SP = 17f
    const val BODY_SP = 14f
    const val LABEL_SP = 12f
    const val CAPTION_SP = 11f
    const val OVERLINE_SP = 11f
    const val OVERLINE_TRACKING = 0.12f
    const val BODY_LINE_SP = 20f
}

object Space {
    const val XS = 4
    const val S = 8
    const val M = 12
    const val L = 16
    const val XL = 20
    const val XXL = 24
    const val XXXL = 32
    const val HUGE = 40
    const val GIANT = 48
}

object Radius {
    const val SMALL = 10
    const val CARD = 14
    const val SHEET = 20
    const val PILL = 999
}

object Motion {
    const val FAST = 150L
    const val BASE = 220L
    const val SLOW = 320L
}

object Theme {

    val SANS: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

    fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    fun box(
        fillColor: Int,
        cornerDp: Int = Radius.CARD,
        density: Float = 1f,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 0
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerDp * density
        setColor(fillColor)
        if (strokeColor != null && strokeWidthDp > 0) {
            setStroke((strokeWidthDp * density + 0.5f).toInt(), strokeColor)
        }
    }

    fun ripple(
        fillColor: Int,
        cornerDp: Int = Radius.CARD,
        density: Float = 1f,
        rippleColor: Int = Palette.RIPPLE,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 0
    ): Drawable {
        val content = box(fillColor, cornerDp, density, strokeColor, strokeWidthDp)
        val mask = box(Color.WHITE, cornerDp, density)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    fun text(
        context: Context,
        value: CharSequence,
        sizeSp: Float = TypeScale.BODY_SP,
        color: Int = Palette.TEXT_PRIMARY,
        typeface: Typeface = SANS,
        letterSpacing: Float = 0f,
        gravity: Int = Gravity.START,
        maxLines: Int = Int.MAX_VALUE
    ): TextView = TextView(context).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        this.typeface = typeface
        this.letterSpacing = letterSpacing
        this.gravity = gravity
        this.maxLines = maxLines
        includeFontPadding = false
    }

    fun applyInsets(
        view: View,
        applyTop: Boolean = true,
        applyBottom: Boolean = true,
        applyLeft: Boolean = false,
        applyRight: Boolean = false
    ) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { v, insets ->
            val leftInset: Int
            val topInset: Int
            val rightInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                leftInset = bars.left
                topInset = bars.top
                rightInset = bars.right
                bottomInset = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                leftInset = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                rightInset = insets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }
            v.setPadding(
                initialLeft + if (applyLeft) leftInset else 0,
                initialTop + if (applyTop) topInset else 0,
                initialRight + if (applyRight) rightInset else 0,
                initialBottom + if (applyBottom) bottomInset else 0
            )
            insets
        }
    }

    fun isReducedMotion(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)

}

fun View.hapticConfirm() {
    val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(feedback)
}

fun View.hapticLight() {
    val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.GESTURE_START
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(feedback)
}

/** Formats a duration as a tabular `mm:ss` clock, clamped at zero. */
fun formatClock(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    return String.format(java.util.Locale.US, "%02d:%02d", safe / 60L, safe % 60L)
}
