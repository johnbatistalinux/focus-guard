package com.focusguard.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets

/**
 * Design system tokens, flat drawable factories, haptics, and system window insets
 * for FocusGuard's dark-only, zero-bloat UI.
 */
object FocusTheme {

    // Canvas & Surface Palette
    const val COLOR_CANVAS: Int = 0xFF0B0F14.toInt()
    const val COLOR_SURFACE: Int = 0xFF131A24.toInt()
    const val COLOR_ELEVATED: Int = 0xFF1C2634.toInt()
    const val COLOR_OVERLAY_BG: Int = 0xF50B0F14.toInt()

    // Brand Accent (Emerald)
    const val COLOR_ACCENT: Int = 0xFF34D399.toInt()
    const val COLOR_ACCENT_SUBTLE: Int = 0x2234D399.toInt()
    const val COLOR_ACCENT_TEXT_DARK: Int = 0xFF062E22.toInt()

    // Typography (All >= 4.5:1 on Surface & Canvas)
    const val COLOR_TEXT_PRIMARY: Int = 0xFFF1F5F9.toInt()
    const val COLOR_TEXT_SECONDARY: Int = 0xFFA0AEC0.toInt()
    const val COLOR_TEXT_MUTED: Int = 0xFF8190A5.toInt()

    // Semantic States
    const val COLOR_WARN: Int = 0xFFF59E0B.toInt()
    const val COLOR_WARN_SUBTLE: Int = 0x22F59E0B.toInt()
    const val COLOR_DANGER: Int = 0xFFF87171.toInt()
    const val COLOR_DANGER_SUBTLE: Int = 0x22F87171.toInt()

    // Interactive
    const val COLOR_RIPPLE: Int = 0x22FFFFFF.toInt()

    fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    fun View.dp(value: Int): Int = FocusTheme.dp(context, value)

    fun flatBox(fillColor: Int, cornerDp: Int = 12, density: Float = 1f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerDp * density
            setColor(fillColor)
        }

    fun flatBoxStroke(
        fillColor: Int,
        strokeColor: Int,
        strokeWidthDp: Int = 1,
        cornerDp: Int = 12,
        density: Float = 1f
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerDp * density
        setColor(fillColor)
        setStroke((strokeWidthDp * density + 0.5f).toInt(), strokeColor)
    }

    fun flatRipple(
        fillColor: Int,
        cornerDp: Int = 12,
        rippleColor: Int = COLOR_RIPPLE,
        density: Float = 1f
    ): Drawable {
        val content = flatBox(fillColor, cornerDp, density)
        val mask = flatBox(Color.WHITE, cornerDp, density)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    fun outlineRipple(
        strokeColor: Int,
        strokeWidthDp: Int = 1,
        cornerDp: Int = 12,
        rippleColor: Int = COLOR_RIPPLE,
        density: Float = 1f
    ): Drawable {
        val content = flatBoxStroke(Color.TRANSPARENT, strokeColor, strokeWidthDp, cornerDp, density)
        val mask = flatBox(Color.WHITE, cornerDp, density)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    fun View.performConfirmHaptic() {
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        performHapticFeedback(feedback)
    }

    fun View.performLightHaptic() {
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_START
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        performHapticFeedback(feedback)
    }

    /**
     * Applies system bars and display cutout insets to view padding,
     * ensuring edge-to-edge compliance on Android 15 (API 35).
     */
    fun applySystemWindowInsetsPadding(
        view: View,
        applyTop: Boolean = true,
        applyBottom: Boolean = true,
        initialPaddingLeft: Int = view.paddingLeft,
        initialPaddingRight: Int = view.paddingRight,
        initialPaddingTop: Int = view.paddingTop,
        initialPaddingBottom: Int = view.paddingBottom
    ) {
        view.setOnApplyWindowInsetsListener { v, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sysBars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                topInset = sysBars.top
                bottomInset = sysBars.bottom
            } else {
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }

            v.setPadding(
                initialPaddingLeft,
                if (applyTop) initialPaddingTop + topInset else initialPaddingTop,
                initialPaddingRight,
                if (applyBottom) initialPaddingBottom + bottomInset else initialPaddingBottom
            )
            insets
        }
    }
}
