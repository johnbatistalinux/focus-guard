package com.focusguard.app.ui

import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import com.focusguard.app.R
import com.focusguard.app.ui.design.ButtonStyle
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.button
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.matchWrap

/**
 * Explicit destructive confirmation sheet, replacing two-tap button mutations.
 */
class ConfirmSheet(
    context: Context,
    private val title: CharSequence,
    private val message: CharSequence,
    private val confirmLabel: CharSequence,
    private val onConfirm: () -> Unit
) : BottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSheetTitle(title)
        val body = Theme.text(
            context,
            message,
            TypeScale.BODY_SP,
            Palette.TEXT_SECONDARY
        ).apply {
            setLineSpacing(context.dp(4).toFloat(), 1f)
            layoutParams = matchWrap(context.dp(Space.S))
        }
        setBodyView(body)

        val footer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        footer.addView(
            button(context, confirmLabel, ButtonStyle.DANGER) {
                onConfirm()
                dismiss()
            }
        )
        footer.addView(
            button(context, context.getString(R.string.action_cancel), ButtonStyle.GHOST) {
                dismiss()
            }.apply {
                layoutParams = matchWrap().apply { topMargin = context.dp(Space.XS) }
            }
        )
        setFooterView(footer)
    }
}
