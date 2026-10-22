package com.focusguard.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.FocusPreset

/**
 * Diálogo nativo escuro para configuração e criação de categorias/grupos de limites.
 */
class GroupConfigDialog(
    private val context: Context,
    private val group: FocusGroup,
    private val isNewGroup: Boolean = false,
    private val isLocked: Boolean = false,
    private val onSave: (FocusGroup) -> Unit,
    private val onDelete: ((FocusGroup) -> Unit)? = null
) {

    private var currentEmoji = group.iconEmoji
    private var mindfulPauseSec = group.mindfulPauseSeconds
    private var sessionDurationMin = group.sessionDurationMinutes
    private var cooldownMin = group.cooldownMinutes
    private var dailyQuotaMin = group.dailyQuotaMinutes
    private var progressivePause = group.progressivePauseEnabled
    private var sharedQuota = group.sharedQuota

    fun show() {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density + 0.5f).toInt()

        val scrollView = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#0B0F19"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(root)

        val titleView = TextView(context).apply {
            text = if (isNewGroup) "✨ Criar Novo Grupo de Limite" else "⚙️ Configurar ${group.iconEmoji} ${group.name}"
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(titleView)

        // Nome do Grupo
        val nameLabel = TextView(context).apply {
            text = "Nome da Categoria:"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(nameLabel)

        val nameInput = EditText(context).apply {
            setText(group.name)
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isEnabled = !isLocked
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(nameInput)

        // Seletor de Emoji
        val emojiRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(12))
        }
        val emojiOptions = listOf("📱", "🎮", "🎬", "🛒", "📰", "💬", "⚡", "🎯")
        val emojiButtons = ArrayList<TextView>()

        for (emoji in emojiOptions) {
            val emojiBtn = TextView(context).apply {
                text = emoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    if (emoji == currentEmoji) {
                        setColor(Color.parseColor("#38BDF8"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                    }
                }
                setOnClickListener {
                    if (isLocked) return@setOnClickListener
                    currentEmoji = emoji
                    emojiButtons.forEach { btn ->
                        (btn.background as? GradientDrawable)?.setColor(
                            if (btn.text == currentEmoji) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B")
                        )
                    }
                }
            }
            emojiButtons.add(emojiBtn)
            emojiRow.addView(emojiBtn)
            (emojiBtn.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(6)
        }
        root.addView(emojiRow)

        // Presets Rápidos
        val presetsLabel = TextView(context).apply {
            text = "Perfis Rápidos de Foco:"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(presetsLabel)

        val presetsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, dp(12))
        }
        val presetsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        presetsScroll.addView(presetsRow)

        fun createStepper(
            label: String,
            getValue: () -> Int,
            unit: String,
            step: Int,
            min: Int,
            max: Int,
            setValue: (Int) -> Unit
        ): View {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#111827"))
                    setStroke(dp(1), Color.parseColor("#1E293B"))
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(8)
                layoutParams = lp
            }

            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val title = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#F8FAFC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
            }
            val valueText = TextView(context).apply {
                text = "${getValue()} $unit"
                setTextColor(Color.parseColor("#38BDF8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            textColumn.addView(title)
            textColumn.addView(valueText)
            container.addView(textColumn)

            val minusBtn = Button(context).apply {
                text = "−"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.parseColor("#F8FAFC"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dp(6).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                isEnabled = !isLocked
                setOnClickListener {
                    val newVal = (getValue() - step).coerceAtLeast(min)
                    setValue(newVal)
                    valueText.text = "$newVal $unit"
                }
            }

            val plusBtn = Button(context).apply {
                text = "+"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.parseColor("#F8FAFC"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dp(6).toFloat()
                }
                val lp = LinearLayout.LayoutParams(dp(36), dp(36))
                lp.marginStart = dp(8)
                layoutParams = lp
                isEnabled = !isLocked
                setOnClickListener {
                    val newVal = (getValue() + step).coerceAtMost(max)
                    setValue(newVal)
                    valueText.text = "$newVal $unit"
                }
            }

            container.addView(minusBtn)
            container.addView(plusBtn)
            return container
        }

        // Steppers
        val dailyQuotaView = createStepper(
            label = "Cota Diária Compartilhada",
            getValue = { dailyQuotaMin },
            unit = "minutos",
            step = 5,
            min = 5,
            max = 240
        ) { dailyQuotaMin = it }

        val sessionDurationView = createStepper(
            label = "Duração de Cada Sessão",
            getValue = { sessionDurationMin },
            unit = "minutos",
            step = 1,
            min = 1,
            max = 60
        ) { sessionDurationMin = it }

        val cooldownView = createStepper(
            label = "Cooldown Pós-Sessão",
            getValue = { cooldownMin },
            unit = "minutos",
            step = 5,
            min = 1,
            max = 180
        ) { cooldownMin = it }

        val pauseView = createStepper(
            label = "Pausa Consciente",
            getValue = { mindfulPauseSec },
            unit = "segundos",
            step = 5,
            min = 3,
            max = 120
        ) { mindfulPauseSec = it }

        // Botoes de Presets
        for (preset in FocusPreset.entries) {
            val presetBtn = Button(context).apply {
                text = preset.title
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#F8FAFC"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dp(6).toFloat()
                }
                setPadding(dp(12), dp(6), dp(12), dp(6))
                isEnabled = !isLocked
                setOnClickListener {
                    mindfulPauseSec = preset.mindfulPauseSeconds
                    sessionDurationMin = preset.sessionDurationMinutes
                    cooldownMin = preset.cooldownMinutes
                    dailyQuotaMin = preset.dailyQuotaMinutes
                    // Atualiza os textos dos steppers
                    ((dailyQuotaView as LinearLayout).getChildAt(0) as LinearLayout).let {
                        (it.getChildAt(1) as TextView).text = "$dailyQuotaMin minutos"
                    }
                    ((sessionDurationView as LinearLayout).getChildAt(0) as LinearLayout).let {
                        (it.getChildAt(1) as TextView).text = "$sessionDurationMin minutos"
                    }
                    ((cooldownView as LinearLayout).getChildAt(0) as LinearLayout).let {
                        (it.getChildAt(1) as TextView).text = "$cooldownMin minutos"
                    }
                    ((pauseView as LinearLayout).getChildAt(0) as LinearLayout).let {
                        (it.getChildAt(1) as TextView).text = "$mindfulPauseSec segundos"
                    }
                }
            }
            presetsRow.addView(presetBtn)
            (presetBtn.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(6)
        }
        root.addView(presetsScroll)

        root.addView(dailyQuotaView)
        root.addView(sessionDurationView)
        root.addView(cooldownView)
        root.addView(pauseView)

        // Switch: Pausa Progressiva
        val progSwitchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val progSwitchText = TextView(context).apply {
            text = "Pausa progressiva (+5s por sessão no grupo)"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val progSwitch = Switch(context).apply {
            isChecked = progressivePause
            isEnabled = !isLocked
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            setOnCheckedChangeListener { _, isChecked -> progressivePause = isChecked }
        }
        progSwitchRow.addView(progSwitchText)
        progSwitchRow.addView(progSwitch)
        root.addView(progSwitchRow)

        // Switch: Cota Compartilhada
        val sharedSwitchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(12))
        }
        val sharedSwitchText = TextView(context).apply {
            text = "Cota e cooldown compartilhados entre apps do grupo"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sharedSwitch = Switch(context).apply {
            isChecked = sharedQuota
            isEnabled = !isLocked
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            setOnCheckedChangeListener { _, isChecked -> sharedQuota = isChecked }
        }
        sharedSwitchRow.addView(sharedSwitchText)
        sharedSwitchRow.addView(sharedSwitch)
        root.addView(sharedSwitchRow)

        // Botão de Excluir Grupo (se customizado e não bloqueado)
        val canDelete = !isNewGroup &&
            group.id != FocusGroup.DEFAULT_GROUP_SOCIAL_ID &&
            group.id != FocusGroup.DEFAULT_GROUP_GAMES_ID &&
            !isLocked

        val dialogBuilder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(scrollView)
            .setPositiveButton("Salvar") { _, _ ->
                val finalName = nameInput.text.toString().trim().ifEmpty { group.name }
                val updatedGroup = group.copy(
                    name = finalName,
                    iconEmoji = currentEmoji,
                    mindfulPauseSeconds = mindfulPauseSec,
                    sessionDurationMinutes = sessionDurationMin,
                    cooldownMinutes = cooldownMin,
                    dailyQuotaMinutes = dailyQuotaMin,
                    progressivePauseEnabled = progressivePause,
                    sharedQuota = sharedQuota
                )
                onSave(updatedGroup)
            }
            .setNegativeButton("Cancelar", null)

        if (canDelete && onDelete != null) {
            dialogBuilder.setNeutralButton("Excluir Grupo") { _, _ ->
                onDelete.invoke(group)
            }
        }

        dialogBuilder.show()
    }
}
