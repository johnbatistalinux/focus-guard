package com.focusguard.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import com.focusguard.app.R
import com.focusguard.app.domain.FocusLimits
import com.focusguard.app.ui.design.ButtonStyle
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.button
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.hapticLight
import com.focusguard.app.ui.design.matchWrap

/**
 * Ulysses Contract pre-commitment bottom sheet: allows the user to lock all
 * configuration mutations for 3, 7, 14, or 30 days.
 */
class CommitmentLockSheet(
    context: Context,
    private val onConfirmDays: (Int) -> Unit
) : BottomSheet(context) {

    private var selectedDays: Int = FocusLimits.COMMITMENT_LOCK_DAY_OPTIONS.first()
    private val radioButtons = ArrayList<RadioButton>()
    private val rowContainers = ArrayList<LinearLayout>()
    private lateinit var confirmBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSheetTitle(context.getString(R.string.commitment_sheet_title))
        setSheetSubtitle(context.getString(R.string.commitment_sheet_subtitle))

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        for ((index, days) in FocusLimits.COMMITMENT_LOCK_DAY_OPTIONS.withIndex()) {
            val isChecked = index == 0
            val radio = RadioButton(context).apply {
                this.isChecked = isChecked
                isClickable = false
                buttonTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Palette.ACCENT, Palette.TEXT_MUTED)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = context.dp(Space.S) }
            }
            radioButtons.add(radio)

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = context.dp(52)
                val padH = context.dp(Space.M)
                setPadding(padH, context.dp(Space.XS), padH, context.dp(Space.XS))
                background = Theme.ripple(
                    if (isChecked) Palette.ACCENT_SUBTLE else Palette.ELEVATED,
                    Radius.SMALL
                )
                isClickable = true
                isFocusable = true
                layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.XS) }
                addView(radio)
                addView(
                    Theme.text(
                        context,
                        context.getString(R.string.commitment_days_option, days),
                        TypeScale.BODY_SP,
                        Palette.TEXT_PRIMARY,
                        Theme.MEDIUM
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                )
                setOnClickListener { view ->
                    selectedDays = days
                    selectOption(index)
                    view.hapticLight()
                }
            }
            rowContainers.add(row)
            body.addView(row)
        }

        body.addView(
            Theme.text(
                context,
                context.getString(R.string.commitment_warning_body),
                TypeScale.CAPTION_SP,
                Palette.WARN
            ).apply {
                setPadding(0, context.dp(Space.M), 0, 0)
                setLineSpacing(context.dp(3).toFloat(), 1f)
            }
        )

        setBodyView(body)
        confirmBtn = button(
            context,
            context.getString(R.string.commitment_confirm_action, selectedDays),
            ButtonStyle.PRIMARY
        ) {
            onConfirmDays(selectedDays)
            dismiss()
        }
        setFooterView(confirmBtn)
    }

    private fun selectOption(selectedIndex: Int) {
        for ((index, radio) in radioButtons.withIndex()) {
            val checked = index == selectedIndex
            radio.isChecked = checked
            rowContainers[index].background = Theme.ripple(
                if (checked) Palette.ACCENT_SUBTLE else Palette.ELEVATED,
                Radius.SMALL
            )
        }
        if (::confirmBtn.isInitialized) {
            confirmBtn.text = context.getString(R.string.commitment_confirm_action, selectedDays)
        }
    }
}
