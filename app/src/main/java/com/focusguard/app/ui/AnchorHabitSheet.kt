package com.focusguard.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import com.focusguard.app.R
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusLimits
import com.focusguard.app.ui.design.ButtonStyle
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.button
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.inputField
import com.focusguard.app.ui.design.matchWrap
import com.focusguard.app.ui.design.hapticLight

/**
 * Anchor habit picker: curated presets plus a sanitized custom habit
 * (max 40 characters), rendered as radio rows with a sticky save action.
 */
class AnchorHabitSheet(
    context: Context,
    private val currentHabit: String,
    private val onHabitConfirmed: (String) -> Unit
) : BottomSheet(context) {

    private var selectedHabit: String = currentHabit
    private lateinit var customEditText: EditText
    private val radioButtons = ArrayList<RadioButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSheetTitle(context.getString(R.string.anchor_title))
        setSheetSubtitle(context.getString(R.string.anchor_subtitle))

        val isPreset = FocusConfig.PRESET_ANCHOR_HABITS.contains(currentHabit)
        val isCustomSelected = !isPreset && currentHabit.isNotBlank()

        val listLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        for (preset in FocusConfig.PRESET_ANCHOR_HABITS) {
            val radio = radioButton(preset == currentHabit)
            radioButtons.add(radio)
            listLayout.addView(selectableRow(radio, preset, preset == currentHabit) { view ->
                selectedHabit = preset
                customEditText.isEnabled = false
                uncheckAllExcept(radio)
                view.hapticLight()
            })
        }

        val customRadio = radioButton(isCustomSelected)
        radioButtons.add(customRadio)
        listLayout.addView(
            selectableRow(
                customRadio,
                context.getString(R.string.anchor_custom_label),
                isCustomSelected
            ) { view ->
                customEditText.isEnabled = true
                customEditText.requestFocus()
                uncheckAllExcept(customRadio)
                view.hapticLight()
            }
        )

        customEditText = inputField(context, context.getString(R.string.anchor_custom_hint)).apply {
            filters = arrayOf(InputFilter.LengthFilter(FocusLimits.MAX_ANCHOR_HABIT_LENGTH))
            setText(if (isCustomSelected) currentHabit else "")
            isEnabled = isCustomSelected
            layoutParams = matchWrap().apply {
                topMargin = context.dp(Space.S)
                bottomMargin = context.dp(Space.M)
            }
        }
        listLayout.addView(customEditText)

        listLayout.addView(
            Theme.text(
                context,
                context.getString(R.string.anchor_current) + ": " + currentHabit,
                TypeScale.CAPTION_SP,
                Palette.TEXT_MUTED
            )
        )

        setBodyView(listLayout)
        setFooterView(
            button(context, context.getString(R.string.action_save), ButtonStyle.PRIMARY) {
                val resolved = if (customEditText.isEnabled) {
                    FocusLimits.sanitizeAnchorHabit(customEditText.text.toString())
                } else {
                    FocusLimits.sanitizeAnchorHabit(selectedHabit)
                }
                onHabitConfirmed(resolved)
                dismiss()
            }
        )
    }

    private fun radioButton(checked: Boolean): RadioButton = RadioButton(context).apply {
        isChecked = checked
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

    private fun selectableRow(
        radio: RadioButton,
        label: CharSequence,
        checked: Boolean,
        onClick: (View) -> Unit
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(52)
        val padH = context.dp(Space.M)
        setPadding(padH, context.dp(Space.XS), padH, context.dp(Space.XS))
        background = Theme.ripple(
            if (checked) Palette.ACCENT_SUBTLE else Palette.ELEVATED,
            Radius.SMALL
        )
        layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.XS) }
        addView(radio)
        addView(
            Theme.text(
                context,
                label,
                TypeScale.BODY_SP,
                Palette.TEXT_PRIMARY,
                Theme.MEDIUM
            ).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        setOnClickListener(onClick)
    }

    private fun uncheckAllExcept(selected: RadioButton) {
        val selectedIndex = radioButtons.indexOf(selected)
        for (index in radioButtons.indices) {
            radioButtons[index].isChecked = index == selectedIndex
            val row = radioButtons[index].parent as? LinearLayout ?: continue
            row.background = Theme.ripple(
                if (index == selectedIndex) Palette.ACCENT_SUBTLE else Palette.ELEVATED,
                Radius.SMALL
            )
        }
    }
}
