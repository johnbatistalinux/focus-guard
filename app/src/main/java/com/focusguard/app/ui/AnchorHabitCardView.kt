package com.focusguard.app.ui

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
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

/**
 * Settings card showing the current anchor habit and a single change action.
 */
class AnchorHabitCardView(
    context: Context,
    onChangeClicked: () -> Unit
) : LinearLayout(context) {

    private val summaryTextView: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Theme.box(Palette.SURFACE, Radius.CARD)
        val pad = context.dp(Space.L)
        setPadding(pad, pad, pad, pad)
        layoutParams = matchWrap()

        val iconBadge = FrameLayout(context).apply {
            val size = context.dp(40)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = context.dp(Space.M)
            }
            background = Theme.box(Palette.SAGE_SUBTLE, Radius.PILL)
        }
        iconBadge.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_leaf)
                setColorFilter(Palette.SAGE, PorterDuff.Mode.SRC_IN)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = FrameLayout.LayoutParams(context.dp(22), context.dp(22), Gravity.CENTER)
            }
        )

        val col = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(
            Theme.text(
                context,
                context.getString(R.string.anchor_title),
                TypeScale.LABEL_SP,
                Palette.TEXT_SECONDARY,
                Theme.MEDIUM
            )
        )
        summaryTextView = Theme.text(
            context,
            "",
            TypeScale.BODY_SP,
            Palette.TEXT_PRIMARY
        ).apply {
            setPadding(0, context.dp(2), 0, 0)
        }
        col.addView(summaryTextView)

        addView(iconBadge)
        addView(col)
        addView(
            button(context, context.getString(R.string.anchor_change), ButtonStyle.GHOST) {
                onChangeClicked()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        )
    }

    fun setAnchorHabit(habitText: String) {
        summaryTextView.text = habitText
    }
}
