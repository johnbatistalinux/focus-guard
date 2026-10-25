package com.focusguard.app.ui

import android.content.Context
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.FocusLimits
import com.focusguard.app.domain.FocusPreset
import com.focusguard.app.domain.GroupIcon
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
import com.focusguard.app.ui.design.themedSeekBar
import com.focusguard.app.ui.design.themedSwitch
import com.focusguard.app.ui.design.hapticLight

/**
 * Group configuration sheet: identity, presets, rhythm sliders, shared quota
 * behavior and an explicit destructive confirmation.
 */
class GroupConfigSheet(
    context: Context,
    private val group: FocusGroup,
    private val isNewGroup: Boolean = false,
    private val isLocked: Boolean = false,
    private val onSave: (FocusGroup) -> Unit,
    private val onDelete: ((FocusGroup) -> Unit)? = null
) : BottomSheet(context) {

    private var currentIcon: GroupIcon = group.icon
    private var mindfulPauseSec = group.mindfulPauseSeconds
    private var sessionDurationMin = group.sessionDurationMinutes
    private var cooldownMin = group.cooldownMinutes
    private var dailyQuotaMin = group.dailyQuotaMinutes
    private var progressivePause = group.progressivePauseEnabled
    private var sharedQuota = group.sharedQuota

    private lateinit var nameInput: android.widget.EditText
    private val presetButtons = ArrayList<Button>()
    private val iconButtons = ArrayList<FrameLayout>()
    private lateinit var sessionSeekBar: SeekBar
    private lateinit var sessionValText: TextView
    private lateinit var quotaSeekBar: SeekBar
    private lateinit var quotaValText: TextView
    private lateinit var pauseValText: TextView
    private lateinit var cooldownValText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSheetTitle(
            context.getString(
                if (isNewGroup) R.string.group_config_new_title else R.string.group_config_edit_title
            )
        )
        setSheetSubtitle(if (isLocked) context.getString(R.string.group_config_subtitle_locked) else null)

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        body.addView(label(R.string.group_config_name_label))
        nameInput = inputField(context, context.getString(R.string.group_config_name_label)).apply {
            setText(group.name)
            isEnabled = !isLocked
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.L) }
        }
        body.addView(nameInput)

        body.addView(label(R.string.group_config_icon_label))
        body.addView(buildIconPicker())

        body.addView(label(R.string.group_config_presets))
        body.addView(buildPresets())

        body.addView(section(R.string.group_config_rhythm))
        val (_, pauseText, pauseRow) = buildSliderRow(
            context.getString(R.string.group_config_pause),
            mindfulPauseSec,
            FocusLimits.PAUSE_SECONDS,
            context.getString(R.string.unit_seconds, mindfulPauseSec)
        ) { value, valueText ->
            mindfulPauseSec = value
            valueText.text = context.getString(R.string.unit_seconds, value)
            updatePresetsHighlight()
        }
        pauseValText = pauseText
        body.addView(pauseRow)

        val (sBar, sText, sRow) = buildSliderRow(
            context.getString(R.string.group_config_session),
            sessionDurationMin,
            FocusLimits.SESSION_MINUTES,
            context.getString(R.string.unit_minutes, sessionDurationMin)
        ) { value, valueText ->
            val (session, quota) = FocusLimits.coerceSessionAndQuota(
                value,
                dailyQuotaMin,
                FocusLimits.ChangedField.SESSION_DURATION
            )
            sessionDurationMin = session
            valueText.text = context.getString(R.string.unit_minutes, session)
            if (quota != dailyQuotaMin) {
                dailyQuotaMin = quota
                syncQuotaBar()
            }
            updatePresetsHighlight()
        }
        sessionSeekBar = sBar
        sessionValText = sText
        body.addView(sRow)

        val (_, cooldownText, cooldownRow) = buildSliderRow(
            context.getString(R.string.group_config_cooldown),
            cooldownMin,
            FocusLimits.COOLDOWN_MINUTES,
            context.getString(R.string.unit_minutes, cooldownMin)
        ) { value, valueText ->
            cooldownMin = value
            valueText.text = context.getString(R.string.unit_minutes, value)
            updatePresetsHighlight()
        }
        cooldownValText = cooldownText
        body.addView(cooldownRow)

        body.addView(section(R.string.group_config_quota))
        val (qBar, qText, qRow) = buildSliderRow(
            label = "",
            initial = dailyQuotaMin,
            range = FocusLimits.DAILY_QUOTA_MINUTES,
            initialLabel = context.getString(R.string.unit_minutes, dailyQuotaMin),
            showLabel = false
        ) { value, valueText ->
            val (session, quota) = FocusLimits.coerceSessionAndQuota(
                sessionDurationMin,
                value,
                FocusLimits.ChangedField.DAILY_QUOTA
            )
            dailyQuotaMin = quota
            valueText.text = context.getString(R.string.unit_minutes, quota)
            if (session != sessionDurationMin) {
                sessionDurationMin = session
                syncSessionBar()
            }
            updatePresetsHighlight()
        }
        quotaSeekBar = qBar
        quotaValText = qText
        body.addView(qRow)
        body.addView(
            Theme.text(
                context,
                context.getString(R.string.group_config_quota_shared_hint),
                TypeScale.CAPTION_SP,
                Palette.TEXT_MUTED
            ).apply {
                setPadding(0, context.dp(Space.XS), 0, context.dp(Space.L))
            }
        )

        body.addView(section(R.string.group_config_behavior))
        body.addView(buildSwitchRow(R.string.group_config_progressive, progressivePause) {
            progressivePause = it
        })
        body.addView(buildSwitchRow(R.string.group_config_shared, sharedQuota) {
            sharedQuota = it
        })

        setBodyView(body)
        updatePresetsHighlight()
        buildFooter()
    }

    private fun label(resId: Int): TextView = Theme.text(
        context,
        context.getString(resId).uppercase(),
        TypeScale.OVERLINE_SP,
        Palette.TEXT_MUTED,
        Theme.MEDIUM,
        TypeScale.OVERLINE_TRACKING
    ).apply {
        setPadding(0, 0, 0, context.dp(Space.S))
    }

    private fun section(resId: Int): TextView = Theme.text(
        context,
        context.getString(resId),
        TypeScale.TITLE_M_SP,
        Palette.TEXT_PRIMARY,
        Theme.MEDIUM
    ).apply {
        setPadding(0, context.dp(Space.M), 0, context.dp(Space.S))
    }

    private fun buildIconPicker(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, context.dp(Space.L))
        }
        for (icon in GroupIcon.entries) {
            val badge = FrameLayout(context).apply {
                val size = context.dp(48)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = context.dp(Space.S)
                }
                background = Theme.box(
                    if (icon == currentIcon) Palette.ACCENT_SUBTLE else Palette.ELEVATED,
                    Radius.SMALL
                )
                isClickable = true
                isFocusable = true
                contentDescription = icon.label
                setOnClickListener { view ->
                    if (isLocked) return@setOnClickListener
                    view.hapticLight()
                    currentIcon = icon
                    refreshIconSelection()
                }
            }
            val image = ImageView(context).apply {
                setImageResource(icon.drawableRes())
                setColorFilter(
                    if (icon == currentIcon) Palette.ACCENT else Palette.TEXT_MUTED,
                    PorterDuff.Mode.SRC_IN
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = FrameLayout.LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER)
            }
            badge.addView(image)
            iconButtons.add(badge)
            row.addView(badge)
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun refreshIconSelection() {
        for ((index, badge) in iconButtons.withIndex()) {
            val icon = GroupIcon.entries[index]
            badge.background = Theme.box(
                if (icon == currentIcon) Palette.ACCENT_SUBTLE else Palette.ELEVATED,
                Radius.SMALL
            )
            (badge.getChildAt(0) as? ImageView)?.setColorFilter(
                if (icon == currentIcon) Palette.ACCENT else Palette.TEXT_MUTED,
                PorterDuff.Mode.SRC_IN
            )
        }
    }

    private fun buildPresets(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, context.dp(Space.M))
        }
        for (preset in FocusPreset.entries) {
            val presetButton = Button(context).apply {
                text = preset.title
                isAllCaps = false
                includeFontPadding = false
                minWidth = 0
                minimumWidth = 0
                minHeight = context.dp(40)
                stateListAnimator = null
                gravity = Gravity.CENTER
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TypeScale.LABEL_SP)
                typeface = Theme.MEDIUM
                isEnabled = !isLocked
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = context.dp(Space.XS)
                }
                setOnClickListener { view ->
                    view.hapticLight()
                    mindfulPauseSec = preset.mindfulPauseSeconds
                    sessionDurationMin = preset.sessionDurationMinutes
                    cooldownMin = preset.cooldownMinutes
                    dailyQuotaMin = preset.dailyQuotaMinutes
                    syncAll()
                }
            }
            presetButtons.add(presetButton)
            row.addView(presetButton)
        }
        return row
    }

    private fun buildSliderRow(
        label: String,
        initial: Int,
        range: IntRange,
        initialLabel: String,
        showLabel: Boolean = true,
        onChange: (Int, TextView) -> Unit
    ): Triple<SeekBar, TextView, View> {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.M) }
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (showLabel) {
            top.addView(
                Theme.text(context, label, TypeScale.LABEL_SP, Palette.TEXT_PRIMARY, Theme.MEDIUM).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
        } else {
            top.addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                }
            )
        }
        val valueText = Theme.text(
            context,
            initialLabel,
            TypeScale.LABEL_SP,
            Palette.ACCENT,
            Theme.MEDIUM
        ).apply {
            fontFeatureSettings = "tnum"
            background = Theme.box(Palette.ACCENT_SUBTLE, Radius.PILL)
            setPadding(context.dp(10), context.dp(3), context.dp(10), context.dp(3))
        }
        top.addView(valueText)
        row.addView(top)
        val seekBar = themedSeekBar(context, range, initial) { value ->
            onChange(value, valueText)
        }.apply {
            isEnabled = !isLocked
            setPadding(0, context.dp(Space.S), 0, 0)
        }
        row.addView(seekBar)
        return Triple(seekBar, valueText, row)
    }

    private fun buildSwitchRow(titleRes: Int, initial: Boolean, onToggle: (Boolean) -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(52)
            addView(
                Theme.text(
                    context,
                    context.getString(titleRes),
                    TypeScale.BODY_SP,
                    Palette.TEXT_SECONDARY
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            val switch = themedSwitch(context, initial).apply {
                isEnabled = !isLocked
                setOnCheckedChangeListener { _, checked -> onToggle(checked) }
            }
            addView(switch)
        }

    private fun syncSessionBar() {
        sessionSeekBar.progress = sessionDurationMin - FocusLimits.SESSION_MINUTES.first
        sessionValText.text = context.getString(R.string.unit_minutes, sessionDurationMin)
    }

    private fun syncQuotaBar() {
        quotaSeekBar.progress = dailyQuotaMin - FocusLimits.DAILY_QUOTA_MINUTES.first
        quotaValText.text = context.getString(R.string.unit_minutes, dailyQuotaMin)
    }

    private fun syncAll() {
        syncSessionBar()
        syncQuotaBar()
        pauseValText.text = context.getString(R.string.unit_seconds, mindfulPauseSec)
        cooldownValText.text = context.getString(R.string.unit_minutes, cooldownMin)
        updatePresetsHighlight()
    }

    private fun updatePresetsHighlight() {
        val matched = FocusLimits.findMatchingPreset(
            mindfulPauseSec,
            sessionDurationMin,
            cooldownMin,
            dailyQuotaMin
        )
        for ((index, preset) in FocusPreset.entries.withIndex()) {
            val isMatched = matched == preset
            presetButtons[index].setTextColor(
                if (isMatched) Palette.ACCENT else Palette.TEXT_SECONDARY
            )
            presetButtons[index].background = Theme.ripple(
                if (isMatched) Palette.ACCENT_SUBTLE else Palette.ELEVATED,
                Radius.SMALL
            )
        }
    }

    private fun buildFooter() {
        val footer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val saveButton = button(context, context.getString(R.string.action_save), ButtonStyle.PRIMARY) {
            val finalName = nameInput.text.toString().trim().ifEmpty { group.name }
            onSave(
                group.copy(
                    name = finalName,
                    iconKey = currentIcon.key,
                    mindfulPauseSeconds = mindfulPauseSec,
                    sessionDurationMinutes = sessionDurationMin,
                    cooldownMinutes = cooldownMin,
                    dailyQuotaMinutes = dailyQuotaMin,
                    progressivePauseEnabled = progressivePause,
                    sharedQuota = sharedQuota
                )
            )
            dismiss()
        }.apply {
            isEnabled = !isLocked
            alpha = if (isLocked) 0.5f else 1f
        }
        footer.addView(saveButton)

        val canDelete = !isNewGroup &&
            group.id != FocusGroup.DEFAULT_GROUP_SOCIAL_ID &&
            group.id != FocusGroup.DEFAULT_GROUP_GAMES_ID &&
            !isLocked &&
            onDelete != null
        if (canDelete) {
            footer.addView(
                button(context, context.getString(R.string.group_config_delete), ButtonStyle.GHOST) {
                    ConfirmSheet(
                        context,
                        context.getString(R.string.group_delete_title),
                        context.getString(R.string.group_delete_body),
                        context.getString(R.string.group_delete_action)
                    ) {
                        onDelete?.invoke(group)
                        dismiss()
                    }.show()
                }.apply {
                    layoutParams = matchWrap().apply { topMargin = context.dp(Space.XS) }
                }
            )
        }
        setFooterView(footer)
    }
}
