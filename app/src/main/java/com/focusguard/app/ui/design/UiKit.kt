package com.focusguard.app.ui.design

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

fun Context.dp(value: Int): Int = Theme.dp(this, value)

fun parseColor(hex: String, fallback: Int = Palette.ACCENT): Int =
    runCatching { Color.parseColor(hex) }.getOrDefault(fallback)

fun withAlpha(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

fun LinearLayout.LayoutParams.margins(
    left: Int = -1,
    top: Int = -1,
    right: Int = -1,
    bottom: Int = -1
): LinearLayout.LayoutParams = apply {
    if (left >= 0) leftMargin = left
    if (top >= 0) topMargin = top
    if (right >= 0) rightMargin = right
    if (bottom >= 0) bottomMargin = bottom
}

fun matchWrap(bottomMargin: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { this.bottomMargin = bottomMargin }

enum class ButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER }

fun button(
    context: Context,
    label: CharSequence,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    onClick: () -> Unit
): Button {
    val fill: Int
    val textColor: Int
    val stroke: Int?
    when (style) {
        ButtonStyle.PRIMARY -> {
            fill = Palette.ACCENT
            textColor = Palette.ACCENT_ON
            stroke = null
        }
        ButtonStyle.SECONDARY -> {
            fill = Palette.ELEVATED
            textColor = Palette.TEXT_PRIMARY
            stroke = Palette.HAIRLINE
        }
        ButtonStyle.GHOST -> {
            fill = Color.TRANSPARENT
            textColor = Palette.ACCENT
            stroke = null
        }
        ButtonStyle.DANGER -> {
            fill = Palette.DANGER_SUBTLE
            textColor = Palette.DANGER
            stroke = null
        }
    }
    val density = context.resources.displayMetrics.density
    return Button(context).apply {
        text = label
        isAllCaps = false
        includeFontPadding = false
        minWidth = 0
        minimumWidth = 0
        minHeight = context.dp(if (style == ButtonStyle.PRIMARY) 52 else 48)
        stateListAnimator = null
        gravity = Gravity.CENTER
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (style == ButtonStyle.PRIMARY) 15f else 14f)
        typeface = Theme.MEDIUM
        background = Theme.ripple(fill, Radius.SMALL, density, strokeColor = stroke, strokeWidthDp = if (stroke != null) 1 else 0)
        val padH = context.dp(20)
        val padV = context.dp(if (style == ButtonStyle.PRIMARY) 14 else 12)
        setPadding(padH, padV, padH, padV)
        layoutParams = matchWrap()
        setOnClickListener { view ->
            view.hapticLight()
            onClick()
        }
    }
}

fun sectionHeader(
    context: Context,
    title: CharSequence,
    actionText: CharSequence? = null,
    onAction: (() -> Unit)? = null
): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = context.dp(48)
    val label = Theme.text(
        context,
        title.toString().uppercase(),
        TypeScale.OVERLINE_SP,
        Palette.TEXT_MUTED,
        Theme.MEDIUM,
        TypeScale.OVERLINE_TRACKING
    ).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    addView(label)
    if (actionText != null && onAction != null) {
        val action = Theme.text(
            context,
            actionText,
            TypeScale.LABEL_SP,
            Palette.ACCENT,
            Theme.MEDIUM
        ).apply {
            gravity = Gravity.CENTER
            val padH = context.dp(10)
            setPadding(padH, context.dp(10), 0, context.dp(10))
            minimumHeight = context.dp(48)
            setOnClickListener { view ->
                view.hapticLight()
                onAction()
            }
        }
        addView(action)
    }
}

fun statusChip(
    context: Context,
    label: CharSequence,
    textColor: Int,
    fillColor: Int
): TextView = Theme.text(
    context,
    label,
    TypeScale.CAPTION_SP,
    textColor,
    Theme.MEDIUM
).apply {
    gravity = Gravity.CENTER
    setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(4))
    minimumHeight = context.dp(28)
    background = Theme.box(fillColor, Radius.PILL)
}

fun monogram(
    context: Context,
    name: CharSequence,
    colorHex: String,
    sizeDp: Int = 40
): TextView {
    val brand = parseColor(colorHex)
    val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    return Theme.text(
        context,
        letter,
        if (sizeDp >= 40) 16f else 13f,
        brand,
        Theme.MEDIUM,
        gravity = Gravity.CENTER
    ).apply {
        val size = context.dp(sizeDp)
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = Theme.box(withAlpha(brand, 0x2E), Radius.PILL)
    }
}

fun themedSwitch(context: Context, checked: Boolean): Switch = Switch(context).apply {
    isChecked = checked
    showText = false
    minimumHeight = context.dp(48)
    gravity = Gravity.CENTER_VERTICAL
    thumbTintList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Palette.ACCENT, Palette.TEXT_MUTED)
    )
    trackTintList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Palette.ACCENT_TRACK, Palette.ELEVATED)
    )
}

fun themedCheckBox(context: Context, checked: Boolean): CheckBox = CheckBox(context).apply {
    isChecked = checked
    isClickable = false
    buttonTintList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Palette.ACCENT, Palette.TEXT_MUTED)
    )
}

fun themedSeekBar(
    context: Context,
    range: IntRange,
    value: Int,
    onChange: (Int) -> Unit
): SeekBar = SeekBar(context).apply {
    max = range.last - range.first
    progress = (value - range.first).coerceIn(0, max)
    progressTintList = ColorStateList.valueOf(Palette.ACCENT)
    thumbTintList = ColorStateList.valueOf(Palette.ACCENT)
    progressBackgroundTintList = ColorStateList.valueOf(Palette.ELEVATED)
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress + range.first)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })
}

fun inputField(
    context: Context,
    hint: CharSequence,
    singleLine: Boolean = true
): EditText = EditText(context).apply {
    this.hint = hint
    setHintTextColor(Palette.TEXT_MUTED)
    setTextColor(Palette.TEXT_PRIMARY)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, TypeScale.BODY_SP)
    typeface = Theme.SANS
    background = Theme.box(Palette.ELEVATED, Radius.SMALL)
    val pad = context.dp(14)
    setPadding(pad, pad, pad, pad)
    isSingleLine = singleLine
    inputType = if (singleLine) {
        android.text.InputType.TYPE_CLASS_TEXT
    } else {
        android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }
    layoutParams = matchWrap()
}

fun emptyState(
    context: Context,
    iconRes: Int,
    title: CharSequence,
    body: CharSequence,
    actionText: CharSequence? = null,
    onAction: (() -> Unit)? = null
): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    val pad = context.dp(Space.XXL)
    setPadding(pad, pad, pad, pad)
    val iconBg = ImageView(context).apply {
        setImageResource(iconRes)
        setColorFilter(Palette.ACCENT, PorterDuff.Mode.SRC_IN)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val size = context.dp(56)
        layoutParams = LinearLayout.LayoutParams(size, size).margins(bottom = context.dp(Space.M))
        setPadding(context.dp(14), context.dp(14), context.dp(14), context.dp(14))
        background = Theme.box(Palette.ACCENT_SUBTLE, Radius.PILL)
    }
    addView(iconBg)
    addView(Theme.text(context, title, TypeScale.TITLE_M_SP, Palette.TEXT_PRIMARY, Theme.MEDIUM, gravity = Gravity.CENTER))
    addView(Theme.text(context, body, TypeScale.BODY_SP, Palette.TEXT_SECONDARY, gravity = Gravity.CENTER).apply {
        setLineSpacing(context.dp(4).toFloat(), 1f)
        setPadding(0, context.dp(Space.XS), 0, context.dp(Space.L))
    })
    if (actionText != null && onAction != null) {
        addView(button(context, actionText, ButtonStyle.SECONDARY, onAction))
    }
}

/**
 * Rounded quota track with a semantic fill. Updated in place to avoid allocations on refresh.
 */
class QuotaBarView(
    context: Context,
    private val heightDp: Int = 8
) : LinearLayout(context) {

    private val fillView: View = View(context)
    private val emptyView: View = View(context)
    private var lastRatio: Float = -1f
    private var lastColor: Int = 0

    init {
        orientation = HORIZONTAL
        background = Theme.box(Palette.SUNKEN, heightDp / 2)
        fillView.background = Theme.box(Palette.ACCENT, heightDp / 2)
        addView(fillView, LayoutParams(0, LayoutParams.MATCH_PARENT, 0.001f))
        addView(emptyView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            context.dp(heightDp)
        )
    }

    fun setProgress(ratio: Float, color: Int) {
        val safe = ratio.coerceIn(0f, 1f)
        if (safe == lastRatio && color == lastColor) return
        lastRatio = safe
        lastColor = color
        fillView.visibility = if (safe <= 0.001f) View.GONE else View.VISIBLE
        emptyView.visibility = if (safe >= 0.999f) View.GONE else View.VISIBLE
        fillView.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, safe.coerceAtLeast(0.001f))
        emptyView.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, (1f - safe).coerceAtLeast(0.001f))
        fillView.background = Theme.box(color, heightDp / 2)
    }
}
