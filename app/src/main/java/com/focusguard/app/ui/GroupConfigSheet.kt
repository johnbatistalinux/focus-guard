package com.focusguard.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.FocusLimits
import com.focusguard.app.domain.FocusPreset
import com.focusguard.app.ui.FocusTheme.dp
import com.focusguard.app.ui.FocusTheme.performLightHaptic

/**
 * Native bottom sheet for configuring group timing parameters, emoji icon,
 * presets, and shared quota policies with live SeekBar coercion (< 250 lines).
 */
class GroupConfigSheet(
    context: Context,
    private val group: FocusGroup,
    private val isNewGroup: Boolean = false,
    private val isLocked: Boolean = false,
    private val onSave: (FocusGroup) -> Unit,
    private val onDelete: ((FocusGroup) -> Unit)? = null
) : BottomSheet(context) {

    private var currentEmoji = group.iconEmoji
    private var mindfulPauseSec = group.mindfulPauseSeconds
    private var sessionDurationMin = group.sessionDurationMinutes
    private var cooldownMin = group.cooldownMinutes
    private var dailyQuotaMin = group.dailyQuotaMinutes
    private var progressivePause = group.progressivePauseEnabled
    private var sharedQuota = group.sharedQuota
    private var isConfirmingDelete = false

    private lateinit var nameInput: EditText
    private val presetButtons = ArrayList<Button>()
    private lateinit var sessionSeekBar: SeekBar
    private lateinit var sessionValText: TextView
    private lateinit var quotaSeekBar: SeekBar
    private lateinit var quotaValText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSheetTitle(if (isNewGroup) "Nova Categoria" else "Configurar Categoria")
        setSheetSubtitle(if (isLocked) "Configurações travadas durante a espera/cota." else null)

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        nameInput = EditText(context).apply {
            setText(group.name); setTextColor(FocusTheme.COLOR_TEXT_PRIMARY); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isEnabled = !isLocked; background = FocusTheme.flatBox(FocusTheme.COLOR_ELEVATED, cornerDp = 8)
            val pad = dp(12); setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        body.addView(nameInput)

        // Emoji selector row (48dp targets)
        val emojiRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        val emojis = listOf("📱", "🎮", "🎬", "🛒", "📰", "💬", "🎯")
        val emojiViews = ArrayList<TextView>()
        for (emoji in emojis) {
            val tv = TextView(context).apply {
                text = emoji; setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); gravity = Gravity.CENTER; minimumHeight = dp(44)
                background = FocusTheme.flatBox(if (emoji == currentEmoji) FocusTheme.COLOR_ACCENT_SUBTLE else FocusTheme.COLOR_ELEVATED, 8)
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) }
                setOnClickListener {
                    if (isLocked) return@setOnClickListener
                    currentEmoji = emoji
                    emojiViews.forEach { v -> v.background = FocusTheme.flatBox(if (v.text == currentEmoji) FocusTheme.COLOR_ACCENT_SUBTLE else FocusTheme.COLOR_ELEVATED, 8) }
                    it.performLightHaptic()
                }
            }
            emojiViews.add(tv); emojiRow.addView(tv)
        }
        body.addView(emojiRow)

        // Presets row
        val presetsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
        }
        for (preset in FocusPreset.entries) {
            val btn = Button(context).apply {
                text = preset.title; isAllCaps = false; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); typeface = Typeface.DEFAULT_BOLD
                isEnabled = !isLocked; minimumHeight = dp(38); layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) }
                setOnClickListener { view ->
                    mindfulPauseSec = preset.mindfulPauseSeconds; sessionDurationMin = preset.sessionDurationMinutes
                    cooldownMin = preset.cooldownMinutes; dailyQuotaMin = preset.dailyQuotaMinutes
                    syncSeekBars(); view.performLightHaptic()
                }
            }
            presetButtons.add(btn); presetsRow.addView(btn)
        }
        body.addView(presetsRow)

        // SeekBars (Cota, Sessão, Espera, Pausa)
        val (qBar, qText, qRow) = buildSeekBarRow("Cota diária", dailyQuotaMin, FocusLimits.DAILY_QUOTA_MINUTES, "min") { v ->
            val (s, q) = FocusLimits.coerceSessionAndQuota(sessionDurationMin, v, FocusLimits.ChangedField.DAILY_QUOTA)
            dailyQuotaMin = q; if (sessionDurationMin != s) { sessionDurationMin = s; syncSessionBar() }; updatePresetsHighlight()
        }
        quotaSeekBar = qBar; quotaValText = qText; body.addView(qRow)

        val (sBar, sText, sRow) = buildSeekBarRow("Duração da sessão", sessionDurationMin, FocusLimits.SESSION_MINUTES, "min") { v ->
            val (s, q) = FocusLimits.coerceSessionAndQuota(v, dailyQuotaMin, FocusLimits.ChangedField.SESSION_DURATION)
            sessionDurationMin = s; if (dailyQuotaMin != q) { dailyQuotaMin = q; syncQuotaBar() }; updatePresetsHighlight()
        }
        sessionSeekBar = sBar; sessionValText = sText; body.addView(sRow)

        val (_, _, cRow) = buildSeekBarRow("Espera pós-sessão", cooldownMin, FocusLimits.COOLDOWN_MINUTES, "min") { v -> cooldownMin = v; updatePresetsHighlight() }
        body.addView(cRow)

        val (_, _, pRow) = buildSeekBarRow("Pausa consciente", mindfulPauseSec, FocusLimits.PAUSE_SECONDS, "s") { v -> mindfulPauseSec = v; updatePresetsHighlight() }
        body.addView(pRow)

        // Switches
        body.addView(buildSwitchRow("Pausa progressiva (+5s por sessão no grupo)", progressivePause) { progressivePause = it })
        body.addView(buildSwitchRow("Cota e espera compartilhadas entre apps", sharedQuota) { sharedQuota = it })

        setBodyView(body)
        updatePresetsHighlight()
        buildFooter()
    }

    private fun buildSeekBarRow(label: String, initial: Int, range: IntRange, unit: String, onChange: (Int) -> Unit): Triple<SeekBar, TextView, View> {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
        }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val lbl = TextView(context).apply {
            text = label; setTextColor(FocusTheme.COLOR_TEXT_PRIMARY); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valTxt = TextView(context).apply { text = "$initial $unit"; setTextColor(FocusTheme.COLOR_ACCENT); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.MONOSPACE }
        top.addView(lbl); top.addView(valTxt); row.addView(top)

        val bar = SeekBar(context).apply {
            max = range.last - range.first; progress = initial - range.first; isEnabled = !isLocked
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    val actual = prog + range.first; valTxt.text = "$actual $unit"
                    if (fromUser) onChange(actual)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        row.addView(bar)
        return Triple(bar, valTxt, row)
    }

    private fun syncSessionBar() {
        sessionSeekBar.progress = sessionDurationMin - FocusLimits.SESSION_MINUTES.first
        sessionValText.text = "$sessionDurationMin min"
    }

    private fun syncQuotaBar() {
        quotaSeekBar.progress = dailyQuotaMin - FocusLimits.DAILY_QUOTA_MINUTES.first
        quotaValText.text = "$dailyQuotaMin min"
    }

    private fun syncSeekBars() {
        syncSessionBar(); syncQuotaBar(); updatePresetsHighlight()
    }

    private fun updatePresetsHighlight() {
        val matched = FocusLimits.findMatchingPreset(mindfulPauseSec, sessionDurationMin, cooldownMin, dailyQuotaMin)
        for ((idx, preset) in FocusPreset.entries.withIndex()) {
            val isMatched = (matched == preset)
            presetButtons[idx].setTextColor(if (isMatched) FocusTheme.COLOR_ACCENT else FocusTheme.COLOR_TEXT_SECONDARY)
            presetButtons[idx].background = FocusTheme.flatBox(if (isMatched) FocusTheme.COLOR_ACCENT_SUBTLE else FocusTheme.COLOR_ELEVATED, 8)
        }
    }

    private fun buildSwitchRow(title: String, isChecked: Boolean, onToggle: (Boolean) -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val padV = dp(6); setPadding(0, padV, 0, padV)
        val tv = TextView(context).apply {
            text = title; setTextColor(FocusTheme.COLOR_TEXT_SECONDARY); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(context).apply { this.isChecked = isChecked; isEnabled = !isLocked; setOnCheckedChangeListener { _, c -> onToggle(c) } }
        addView(tv); addView(sw)
    }

    private fun buildFooter() {
        val footer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val saveBtn = Button(context).apply {
            text = context.getString(R.string.action_save); isAllCaps = false
            setTextColor(FocusTheme.COLOR_ACCENT_TEXT_DARK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(48); background = FocusTheme.flatRipple(FocusTheme.COLOR_ACCENT, cornerDp = 12)
            isEnabled = !isLocked
            setOnClickListener {
                val finalName = nameInput.text.toString().trim().ifEmpty { group.name }
                onSave(group.copy(
                    name = finalName, iconEmoji = currentEmoji, mindfulPauseSeconds = mindfulPauseSec,
                    sessionDurationMinutes = sessionDurationMin, cooldownMinutes = cooldownMin, dailyQuotaMinutes = dailyQuotaMin,
                    progressivePauseEnabled = progressivePause, sharedQuota = sharedQuota
                ))
                dismiss()
            }
        }
        footer.addView(saveBtn)

        val canDelete = !isNewGroup && group.id != FocusGroup.DEFAULT_GROUP_SOCIAL_ID && group.id != FocusGroup.DEFAULT_GROUP_GAMES_ID && !isLocked
        if (canDelete && onDelete != null) {
            val delBtn = Button(context).apply {
                text = context.getString(R.string.action_delete_group); isAllCaps = false
                setTextColor(FocusTheme.COLOR_DANGER); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                background = FocusTheme.flatRipple(Color.TRANSPARENT, cornerDp = 8)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
                setOnClickListener {
                    if (!isConfirmingDelete) {
                        isConfirmingDelete = true
                        text = context.getString(R.string.action_delete_confirm)
                        setTextColor(Color.WHITE)
                        background = FocusTheme.flatBox(FocusTheme.COLOR_DANGER, cornerDp = 8)
                    } else {
                        onDelete.invoke(group); dismiss()
                    }
                }
            }
            footer.addView(delBtn)
        }
        setFooterView(footer)
    }
}
