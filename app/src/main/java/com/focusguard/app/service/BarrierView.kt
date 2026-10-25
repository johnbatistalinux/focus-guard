package com.focusguard.app.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.domain.BlockReason
import com.focusguard.app.domain.InterventionAction
import com.focusguard.app.ui.design.ButtonStyle
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.button
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.formatClock
import com.focusguard.app.ui.design.matchWrap

/**
 * Full-screen barrier used by both the mindful pause and the hard block.
 * The view is built once and rebound in place so the 1 Hz ticker never
 * allocates a new hierarchy.
 */
class BarrierView(
    context: Context,
    private val reducedMotion: Boolean,
    private val onExitToHome: () -> Unit,
    private val onConfirmSession: (Boolean) -> Unit
) : LinearLayout(context) {

    private val overline: TextView
    private val title: TextView
    private val ringContainer: FrameLayout
    private val ringView: View
    private val counter: TextView
    private val cue: TextView
    private val quotaContainer: LinearLayout
    private val quotaFill: View
    private val quotaInfo: TextView
    private val reclaimedHint: TextView
    private val exitButton: Button
    private val proceedButton: Button
    private val quickButton: Button

    private var breathingAnimator: ValueAnimator? = null
    private var isMindfulPauseActive: Boolean = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Palette.OVERLAY_BG)
        setPadding(context.dp(Space.XXL), context.dp(Space.XXXL), context.dp(Space.XXL), context.dp(Space.XXXL))
        isFocusable = true
        isFocusableInTouchMode = true
        Theme.applyInsets(this, applyTop = true, applyBottom = true)

        overline = Theme.text(
            context,
            "",
            TypeScale.OVERLINE_SP,
            Palette.TEXT_MUTED,
            Theme.MEDIUM,
            TypeScale.OVERLINE_TRACKING,
            Gravity.CENTER
        ).apply {
            setPadding(0, 0, 0, context.dp(Space.S))
            maxLines = 1
        }

        title = Theme.text(
            context,
            "",
            TypeScale.TITLE_L_SP,
            Palette.TEXT_PRIMARY,
            Theme.MEDIUM,
            gravity = Gravity.CENTER,
            maxLines = 2
        )

        ringContainer = FrameLayout(context).apply {
            val size = context.dp(148)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, context.dp(Space.XL), 0, context.dp(Space.XL))
            }
        }
        ringView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Palette.ACCENT_SUBTLE)
                setStroke(context.dp(1), Palette.ACCENT_TRACK)
            }
        }
        counter = Theme.text(
            context,
            "",
            TypeScale.TIMER_SP,
            Palette.ACCENT,
            Theme.LIGHT,
            gravity = Gravity.CENTER
        ).apply {
            fontFeatureSettings = "tnum"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        ringContainer.addView(ringView)
        ringContainer.addView(counter)

        cue = Theme.text(
            context,
            "",
            TypeScale.BODY_SP,
            Palette.TEXT_SECONDARY,
            gravity = Gravity.CENTER
        ).apply {
            setLineSpacing(context.dp(4).toFloat(), 1f)
            setPadding(0, 0, 0, context.dp(Space.XL))
        }

        quotaContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.XL) }
        }
        val track = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(context.dp(200), context.dp(6)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = context.dp(Space.S)
            }
            background = Theme.box(Palette.ELEVATED, Radius.PILL)
        }
        quotaFill = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.7f)
            background = Theme.box(Palette.ACCENT, Radius.PILL)
        }
        track.addView(quotaFill)
        quotaInfo = Theme.text(
            context,
            "",
            TypeScale.CAPTION_SP,
            Palette.TEXT_MUTED,
            gravity = Gravity.CENTER
        )
        quotaContainer.addView(track)
        quotaContainer.addView(quotaInfo)

        reclaimedHint = Theme.text(
            context,
            "",
            TypeScale.CAPTION_SP,
            Palette.SAGE,
            Theme.MEDIUM,
            gravity = Gravity.CENTER
        ).apply {
            visibility = GONE
            setPadding(0, 0, 0, context.dp(Space.M))
        }

        exitButton = button(context, "", ButtonStyle.PRIMARY) {
            onExitToHome()
        }.apply {
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.S) }
        }

        proceedButton = button(context, "", ButtonStyle.SECONDARY) {
            onConfirmSession(false)
        }.apply {
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.S) }
        }

        quickButton = button(context, context.getString(R.string.overlay_quick), ButtonStyle.GHOST) {
            onConfirmSession(true)
        }.apply {
            visibility = GONE
        }

        addView(overline)
        addView(title)
        addView(ringContainer)
        addView(cue)
        addView(quotaContainer)
        addView(reclaimedHint)
        addView(exitButton)
        addView(proceedButton)
        addView(quickButton)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onExitToHome()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    fun showMindfulPause(action: InterventionAction.ShowMindfulPause) {
        isMindfulPauseActive = true

        overline.visibility = VISIBLE
        overline.text = if (action.groupName.isNotBlank()) {
            context.getString(
                R.string.overlay_pause_overline,
                action.groupName,
                action.displayName
            ).uppercase()
        } else {
            context.getString(R.string.overlay_pause_overline_simple, action.displayName).uppercase()
        }
        overline.setTextColor(Palette.TEXT_MUTED)

        title.text = context.getString(
            if (action.readyToStartSession) R.string.overlay_pause_title_ready
            else R.string.overlay_pause_title
        )

        ringContainer.visibility = VISIBLE
        counter.visibility = VISIBLE
        if (action.readyToStartSession) {
            stopBreathing()
            counter.text = context.getString(R.string.overlay_breathe_ready)
            counter.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TypeScale.TITLE_M_SP)
        } else {
            counter.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TypeScale.TIMER_SP)
            counter.text = formatClock(action.remainingPauseSeconds.toLong())
            startBreathing()
        }
        counter.setTextColor(Palette.ACCENT)

        cue.visibility = VISIBLE
        cue.text = if (action.breathingCue.isNotBlank()) {
            action.breathingCue
        } else {
            context.getString(
                if (action.readyToStartSession) R.string.overlay_pause_cue_ready
                else R.string.overlay_pause_cue
            )
        }

        quotaContainer.visibility = VISIBLE
        quotaInfo.text = context.getString(
            R.string.overlay_quota_remaining,
            formatClock(action.remainingDailyQuotaSeconds)
        )
        val ratio = (action.remainingDailyQuotaSeconds / 3600f).coerceIn(0.02f, 1f)
        quotaFill.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            ratio
        )
        quotaFill.background = Theme.box(
            when {
                action.remainingDailyQuotaSeconds > 900L -> Palette.ACCENT
                action.remainingDailyQuotaSeconds > 300L -> Palette.WARN
                else -> Palette.DANGER
            },
            Radius.PILL
        )

        exitButton.text = action.anchorActionLabel
        exitButton.isEnabled = true
        exitButton.alpha = 1f

        reclaimedHint.visibility = if (action.reclaimedMinutesOnAbort > 0) VISIBLE else GONE
        reclaimedHint.text = context.getString(
            R.string.overlay_reclaimed_hint,
            action.reclaimedMinutesOnAbort
        )

        proceedButton.visibility = VISIBLE
        proceedButton.isEnabled = action.readyToStartSession
        proceedButton.alpha = if (action.readyToStartSession) 1f else 0.4f
        proceedButton.text = if (action.readyToStartSession) {
            context.getString(R.string.overlay_continue, action.sessionDurationMinutes)
        } else {
            context.getString(R.string.overlay_waiting, action.remainingPauseSeconds)
        }

        quickButton.visibility = if (action.allowQuickMicroSession) VISIBLE else GONE
        quickButton.isEnabled = action.allowQuickMicroSession
    }

    fun showHardBlock(action: InterventionAction.ShowHardBlock) {
        isMindfulPauseActive = false
        stopBreathing()

        val isDailyExhausted = action.reason == BlockReason.DAILY_QUOTA_EXCEEDED

        overline.visibility = VISIBLE
        overline.text = context.getString(R.string.overlay_block_overline, action.displayName)
        overline.setTextColor(Palette.DANGER)

        title.text = if (isDailyExhausted) {
            context.getString(R.string.overlay_block_quota_title)
        } else {
            context.getString(
                R.string.overlay_block_cooldown_title,
                unlockClockTime(action.remainingBlockSeconds)
            )
        }

        ringContainer.visibility = GONE

        cue.visibility = VISIBLE
        cue.text = when {
            isDailyExhausted && action.groupName.isNotBlank() ->
                context.getString(R.string.overlay_block_quota_body_group, action.groupName)
            isDailyExhausted -> context.getString(R.string.overlay_block_quota_body)
            action.groupName.isNotBlank() ->
                context.getString(R.string.overlay_block_cooldown_body_group, action.groupName)
            else -> context.getString(R.string.overlay_block_cooldown_body)
        }

        quotaContainer.visibility = GONE
        reclaimedHint.visibility = GONE
        proceedButton.visibility = GONE
        quickButton.visibility = GONE

        exitButton.text = context.getString(R.string.overlay_block_return_home)
        exitButton.isEnabled = true
        exitButton.alpha = 1f
    }

    fun startBreathing() {
        if (reducedMotion) return
        if (breathingAnimator != null) return
        breathingAnimator = ValueAnimator.ofFloat(0.9f, 1.1f).apply {
            duration = 4000L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val scale = anim.animatedValue as Float
                ringView.scaleX = scale
                ringView.scaleY = scale
            }
            start()
        }
    }

    fun stopBreathing() {
        breathingAnimator?.cancel()
        breathingAnimator = null
        ringView.scaleX = 1f
        ringView.scaleY = 1f
    }

    private fun unlockClockTime(remainingSeconds: Long): String = runCatching {
        java.time.LocalTime.now()
            .plusSeconds(remainingSeconds.coerceAtLeast(0L))
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("--:--")
}
