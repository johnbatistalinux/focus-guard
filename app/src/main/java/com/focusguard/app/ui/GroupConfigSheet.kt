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
import com.focusguard.app.data.SystemTimeProvider
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
    private var taperingEnabled = group.taperingEnabled
    private var taperingTargetMin = group.taperingTargetQuotaMinutes.coerceIn(
        group.sessionDurationMinutes,
        group.dailyQuotaMinutes
    )
    private var taperingStartEpochDay = group.taperingStartEpochDay
    private var quietHoursEnabled = group.quietHoursEnabled
    private var quietHoursStartMin = group.quietHoursStartMinute
    private var quietHoursEndMin = group.quietHoursEndMinute

    private lateinit var nameInput: android.widget.EditText
    private val presetButtons = ArrayList<Button>()
    private val iconButtons = ArrayList<FrameLayout>()
    private val quietStartButtons = ArrayList<Button>()
    private val quietEndButtons = ArrayList<Button>()
    private lateinit var sessionSeekBar: SeekBar
    private lateinit var sessionValText: TextView
    private lateinit var quotaSeekBar: SeekBar
    private lateinit var quotaValText: TextView
    private lateinit var pauseValText: TextView
    private lateinit var cooldownValText: TextView
    private lateinit var taperingDetailContainer: LinearLayout
    private lateinit var taperingSeekBar: SeekBar
    private lateinit var taperingValText: TextView
    private lateinit var quietHoursDetailContainer: LinearLayout

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
            if (taperingTargetMin > dailyQuotaMin) {
                taperingTargetMin = dailyQuotaMin
                syncTaperingBar()
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
        body.addView(buildSwitchRow(R.string.group_config_tapering, taperingEnabled) { enabled ->
            taperingEnabled = enabled
            if (enabled && taperingStartEpochDay <= 0L) {
                taperingStartEpochDay = SystemTimeProvider.currentEpochDay()
            }
            taperingDetailContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        })
        taperingDetailContainer = buildTaperingDetail()
        body.addView(taperingDetailContainer)

        body.addView(buildSwitchRow(R.string.group_config_quiet_hours, quietHoursEnabled) { enabled ->
            quietHoursEnabled = enabled
            quietHoursDetailContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        })
        quietHoursDetailContainer = buildQuietHoursDetail()
        body.addView(quietHoursDetailContainer)

        setBodyView(body)
        updatePresetsHighlight()
        updateQuietHoursHighlights()
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

    private fun syncTaperingBar() {
        if (!::taperingSeekBar.isInitialized) return
        taperingTargetMin = taperingTargetMin.coerceIn(
            maxOf(sessionDurationMin, FocusLimits.DAILY_QUOTA_MINUTES.first),
            dailyQuotaMin
        )
        taperingSeekBar.progress = taperingTargetMin - FocusLimits.DAILY_QUOTA_MINUTES.first
        taperingValText.text = context.getString(R.string.unit_minutes, taperingTargetMin)
    }

    private fun syncAll() {
        syncSessionBar()
        syncQuotaBar()
        syncTaperingBar()
        pauseValText.text = context.getString(R.string.unit_seconds, mindfulPauseSec)
        cooldownValText.text = context.getString(R.string.unit_minutes, cooldownMin)
        updatePresetsHighlight()
    }

    private fun buildTaperingDetail(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = if (taperingEnabled) View.VISIBLE else View.GONE
        setPadding(0, context.dp(Space.XS), 0, context.dp(Space.S))

        val (tBar, tText, tRow) = buildSliderRow(
            context.getString(R.string.group_config_tapering_target),
            taperingTargetMin,
            FocusLimits.DAILY_QUOTA_MINUTES,
            context.getString(R.string.unit_minutes, taperingTargetMin)
        ) { value, valueText ->
            val clamped = value.coerceIn(
                maxOf(sessionDurationMin, FocusLimits.DAILY_QUOTA_MINUTES.first),
                dailyQuotaMin
            )
            taperingTargetMin = clamped
            valueText.text = context.getString(R.string.unit_minutes, clamped)
            if (clamped != value) {
                syncTaperingBar()
            }
        }
        taperingSeekBar = tBar
        taperingValText = tText
        addView(tRow)
        addView(
            Theme.text(
                context,
                context.getString(R.string.group_config_tapering_hint),
                TypeScale.CAPTION_SP,
                Palette.TEXT_MUTED
            ).apply {
                setPadding(0, 0, 0, context.dp(Space.S))
            }
        )
    }

    private fun buildQuietHoursDetail(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = if (quietHoursEnabled) View.VISIBLE else View.GONE
        setPadding(0, context.dp(Space.XS), 0, context.dp(Space.S))

        addView(label(R.string.group_config_quiet_start))
        addView(
            buildTimePresetRow(FocusLimits.QUIET_START_PRESETS, quietStartButtons) { chosen ->
                quietHoursStartMin = chosen
                updateQuietHoursHighlights()
            }
        )

        addView(label(R.string.group_config_quiet_end))
        addView(
            buildTimePresetRow(FocusLimits.QUIET_END_PRESETS, quietEndButtons) { chosen ->
                quietHoursEndMin = chosen
                updateQuietHoursHighlights()
            }
        )

        addView(
            Theme.text(
                context,
                context.getString(R.string.group_config_quiet_hint),
                TypeScale.CAPTION_SP,
                Palette.TEXT_MUTED
            ).apply {
                setPadding(0, 0, 0, context.dp(Space.S))
            }
        )
    }

    private fun buildTimePresetRow(
        presets: List<Int>,
        targetButtons: MutableList<Button>,
        onSelect: (Int) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, context.dp(Space.M))
        }
        for (minute in presets) {
            val btn = Button(context).apply {
                text = FocusLimits.formatMinuteOfDay(minute)
                isAllCaps = false
                includeFontPadding = false
                minWidth = 0
                minimumWidth = 0
                minHeight = context.dp(36)
                stateListAnimator = null
                gravity = Gravity.CENTER
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TypeScale.CAPTION_SP)
                typeface = Theme.MEDIUM
                isEnabled = !isLocked
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = context.dp(Space.XS)
                }
                setOnClickListener { view ->
                    view.hapticLight()
                    onSelect(minute)
                }
            }
            targetButtons.add(btn)
            row.addView(btn)
        }
        return row
    }

    private fun updateQuietHoursHighlights() {
        for ((index, minute) in FocusLimits.QUIET_START_PRESETS.withIndex()) {
            val btn = quietStartButtons.getOrNull(index) ?: continue
            val isSelected = minute == quietHoursStartMin
            btn.setTextColor(if (isSelected) Palette.ACCENT else Palette.TEXT_SECONDARY)
            btn.background = Theme.ripple(
                if (isSelected) Palette.ACCENT_SUBTLE else Palette.ELEVATED,
                Radius.SMALL
            )
        }
        for ((index, minute) in FocusLimits.QUIET_END_PRESETS.withIndex()) {
            val btn = quietEndButtons.getOrNull(index) ?: continue
            val isSelected = minute == quietHoursEndMin
            btn.setTextColor(if (isSelected) Palette.ACCENT else Palette.TEXT_SECONDARY)
            btn.background = Theme.ripple(
                if (isSelected) Palette.ACCENT_SUBTLE else Palette.ELEVATED,
                Radius.SMALL
            )
        }
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
            val finalTaperingTarget = taperingTargetMin.coerceIn(
                maxOf(sessionDurationMin, FocusLimits.DAILY_QUOTA_MINUTES.first),
                dailyQuotaMin
            )
            val finalTaperingStart = if (taperingEnabled) {
                if (taperingStartEpochDay > 0L) taperingStartEpochDay else SystemTimeProvider.currentEpochDay()
            } else {
                0L
            }
            onSave(
                group.copy(
                    name = finalName,
                    iconKey = currentIcon.key,
                    mindfulPauseSeconds = mindfulPauseSec,
                    sessionDurationMinutes = sessionDurationMin,
                    cooldownMinutes = cooldownMin,
                    dailyQuotaMinutes = dailyQuotaMin,
                    progressivePauseEnabled = progressivePause,
                    sharedQuota = sharedQuota,
                    taperingEnabled = taperingEnabled,
                    taperingTargetQuotaMinutes = finalTaperingTarget,
                    taperingStartEpochDay = finalTaperingStart,
                    quietHoursEnabled = quietHoursEnabled,
                    quietHoursStartMinute = quietHoursStartMin,
                    quietHoursEndMinute = quietHoursEndMin
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
