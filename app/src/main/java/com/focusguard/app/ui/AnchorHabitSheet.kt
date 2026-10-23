package com.focusguard.app.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusLimits
import com.focusguard.app.ui.FocusTheme.performLightHaptic

/**
 * Bottom sheet allowing selection of preconfigured anchor habits
 * or defining a sanitized custom habit (max 40 chars).
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
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density + 0.5f).toInt()

        setSheetTitle("Hábito Âncora do Foco")
        setSheetSubtitle("Meta exibida no botão verde de desistência consciente.")

        val isPreset = FocusConfig.PRESET_ANCHOR_HABITS.contains(currentHabit)
        val isCustomSelected = !isPreset && currentHabit.isNotBlank()

        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Preset Options
        for (preset in FocusConfig.PRESET_ANCHOR_HABITS) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                background = FocusTheme.flatRipple(FocusTheme.COLOR_SURFACE, cornerDp = 8, density = density)
                val padH = dp(12)
                setPadding(padH, dp(6), padH, dp(6))
            }

            val radio = RadioButton(context).apply {
                isChecked = (preset == currentHabit)
                isClickable = false
            }
            radioButtons.add(radio)

            val text = TextView(context).apply {
                this.text = preset
                setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), 0, 0, 0)
            }

            row.addView(radio)
            row.addView(text)
            row.setOnClickListener { view ->
                selectedHabit = preset
                customEditText.isEnabled = false
                uncheckAllExcept(radio)
                view.performLightHaptic()
            }
            listLayout.addView(row)
        }

        // 2. Custom Option
        val customRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            background = FocusTheme.flatRipple(FocusTheme.COLOR_SURFACE, cornerDp = 8, density = density)
            val padH = dp(12)
            setPadding(padH, dp(6), padH, dp(6))
        }

        val customRadio = RadioButton(context).apply {
            isChecked = isCustomSelected
            isClickable = false
        }
        radioButtons.add(customRadio)

        val customLabel = TextView(context).apply {
            text = "Personalizado (máx. 40 caracteres):"
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), 0, 0, 0)
        }

        customRow.addView(customRadio)
        customRow.addView(customLabel)
        customRow.setOnClickListener { view ->
            selectedHabit = customEditText.text.toString()
            customEditText.isEnabled = true
            customEditText.requestFocus()
            uncheckAllExcept(customRadio)
            view.performLightHaptic()
        }
        listLayout.addView(customRow)

        customEditText = EditText(context).apply {
            hint = "Ex: Meditar 10 minutos 🧘"
            setHintTextColor(FocusTheme.COLOR_TEXT_MUTED)
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            filters = arrayOf(InputFilter.LengthFilter(FocusLimits.MAX_ANCHOR_HABIT_LENGTH))
            background = FocusTheme.flatBox(FocusTheme.COLOR_ELEVATED, cornerDp = 8, density = density)
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
            isEnabled = isCustomSelected
            if (isCustomSelected) {
                setText(currentHabit)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
            layoutParams = lp
        }
        listLayout.addView(customEditText)

        setBodyView(listLayout)

        // 3. Footer Action Buttons
        val footerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val saveBtn = Button(context).apply {
            text = context.getString(R.string.action_save)
            isAllCaps = false
            setTextColor(FocusTheme.COLOR_ACCENT_TEXT_DARK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(48)
            background = FocusTheme.flatRipple(FocusTheme.COLOR_ACCENT, cornerDp = 12, density = density)
            setOnClickListener {
                val finalHabit = if (customRadio.isChecked) {
                    FocusLimits.sanitizeAnchorHabit(customEditText.text.toString())
                } else {
                    FocusLimits.sanitizeAnchorHabit(selectedHabit)
                }
                onHabitConfirmed(finalHabit)
                dismiss()
            }
        }

        val cancelBtn = Button(context).apply {
            text = context.getString(R.string.action_cancel)
            isAllCaps = false
            setTextColor(FocusTheme.COLOR_TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = FocusTheme.flatRipple(android.graphics.Color.TRANSPARENT, cornerDp = 12, density = density)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            layoutParams = lp
            setOnClickListener { dismiss() }
        }

        footerLayout.addView(saveBtn)
        footerLayout.addView(cancelBtn)
        setFooterView(footerLayout)
    }

    private fun uncheckAllExcept(active: RadioButton) {
        for (rb in radioButtons) {
            rb.isChecked = (rb === active)
        }
    }
}
