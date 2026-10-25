package com.focusguard.app.ui

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.ui.design.ButtonStyle
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.button
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.matchWrap
import com.focusguard.app.ui.design.statusChip

/**
 * Accessibility-service card used both as a compact callout (Hoje) and as a
 * step-by-step activation guide (Ajustes).
 */
class ServiceStatusCard(
    context: Context,
    private val detailed: Boolean,
    private val onActivate: () -> Unit,
    private val onTest: () -> Unit
) : LinearLayout(context) {

    private val iconBadge: FrameLayout
    private val iconView: ImageView
    private val titleView: TextView
    private val bodyView: TextView
    private val chip: TextView
    private val stepsContainer: LinearLayout
    private val activateButton: android.widget.Button
    private val testButton: android.widget.Button

    init {
        orientation = VERTICAL
        background = Theme.box(Palette.SURFACE, Radius.CARD)
        val pad = context.dp(Space.L)
        setPadding(pad, pad, pad, pad)
        layoutParams = matchWrap()

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.TOP
        }

        iconBadge = FrameLayout(context).apply {
            val size = context.dp(40)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = context.dp(Space.M) }
            background = Theme.box(Palette.WARN_SUBTLE, Radius.PILL)
        }
        iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val size = context.dp(22)
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        }
        iconBadge.addView(iconView)

        val textCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleView = Theme.text(context, "", TypeScale.TITLE_M_SP, Palette.TEXT_PRIMARY, Theme.MEDIUM)
        bodyView = Theme.text(context, "", TypeScale.CAPTION_SP, Palette.TEXT_SECONDARY).apply {
            setLineSpacing(context.dp(3).toFloat(), 1f)
            setPadding(0, context.dp(Space.XS), 0, 0)
        }
        textCol.addView(titleView)
        textCol.addView(bodyView)
        header.addView(iconBadge)
        header.addView(textCol)

        chip = statusChip(context, "", Palette.TEXT_MUTED, Palette.ELEVATED).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = context.dp(Space.S) }
        }
        header.addView(chip)
        addView(header)

        stepsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            setPadding(0, context.dp(Space.M), 0, 0)
        }
        if (detailed) addView(stepsContainer)

        activateButton = button(context, context.getString(R.string.service_activate), ButtonStyle.PRIMARY) {
            onActivate()
        }.apply {
            layoutParams = matchWrap().apply { topMargin = context.dp(Space.M) }
        }
        testButton = button(context, context.getString(R.string.service_test_barrier), ButtonStyle.SECONDARY) {
            onTest()
        }.apply {
            layoutParams = matchWrap().apply { topMargin = context.dp(Space.M) }
            visibility = View.GONE
        }
        addView(activateButton)
        if (detailed) addView(testButton)
    }

    fun bind(serviceActive: Boolean) {
        val accent = if (serviceActive) Palette.SAGE else Palette.WARN
        val subtle = if (serviceActive) Palette.SAGE_SUBTLE else Palette.WARN_SUBTLE
        background = Theme.box(Palette.SURFACE, Radius.CARD)
        iconBadge.background = Theme.box(subtle, Radius.PILL)
        iconView.setImageResource(if (serviceActive) R.drawable.ic_shield else R.drawable.ic_lock)
        iconView.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        titleView.text = context.getString(
            if (serviceActive) R.string.service_active_title else R.string.service_inactive_title
        )
        bodyView.text = context.getString(
            if (serviceActive) R.string.service_active_body else R.string.service_inactive_body
        )
        chip.visibility = if (detailed) View.VISIBLE else View.GONE
        chip.text = context.getString(
            if (serviceActive) R.string.status_protected else R.string.status_inactive
        )
        chip.setTextColor(accent)
        chip.background = Theme.box(subtle, Radius.PILL)

        stepsContainer.removeAllViews()
        if (detailed && !serviceActive) {
            stepsContainer.visibility = View.VISIBLE
            stepsContainer.addView(
                Theme.text(
                    context,
                    context.getString(R.string.service_steps_title).uppercase(),
                    TypeScale.OVERLINE_SP,
                    Palette.TEXT_MUTED,
                    Theme.MEDIUM,
                    TypeScale.OVERLINE_TRACKING
                ).apply { setPadding(0, 0, 0, context.dp(Space.XS)) }
            )
            val steps = listOf(
                R.string.service_step_1,
                R.string.service_step_2,
                R.string.service_step_3
            )
            for ((index, stepRes) in steps.withIndex()) {
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.TOP
                    setPadding(0, context.dp(Space.XS), 0, context.dp(Space.XS))
                }
                val number = Theme.text(
                    context,
                    (index + 1).toString(),
                    TypeScale.CAPTION_SP,
                    Palette.ACCENT,
                    Theme.MEDIUM,
                    gravity = Gravity.CENTER
                ).apply {
                    val size = context.dp(22)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = context.dp(Space.S)
                    }
                    background = Theme.box(Palette.ACCENT_SUBTLE, Radius.PILL)
                }
                row.addView(number)
                row.addView(
                    Theme.text(
                        context,
                        context.getString(stepRes),
                        TypeScale.CAPTION_SP,
                        Palette.TEXT_SECONDARY
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setLineSpacing(context.dp(2).toFloat(), 1f)
                    }
                )
                stepsContainer.addView(row)
            }
        } else {
            stepsContainer.visibility = View.GONE
        }

        activateButton.visibility = if (serviceActive) View.GONE else View.VISIBLE
        testButton.visibility = if (detailed && serviceActive) View.VISIBLE else View.GONE
    }
}
