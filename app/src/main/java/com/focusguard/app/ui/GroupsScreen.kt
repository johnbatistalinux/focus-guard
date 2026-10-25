package com.focusguard.app.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import com.focusguard.app.R
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.ui.design.ButtonStyle
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.button
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.matchWrap
import com.focusguard.app.ui.presenter.TodayState

/**
 * "Grupos" tab: every monitored category with its quota, cooldown state,
 * per-app usage and the configuration entry points.
 */
class GroupsScreen(
    context: Context,
    private val onNewGroup: () -> Unit,
    private val onToggleGroup: (FocusGroup, Boolean) -> Unit,
    private val onEditGroup: (FocusGroup) -> Unit,
    private val onManageApps: (FocusGroup) -> Unit,
    private val onLockedAttempt: () -> Unit
) : ScrollView(context) {

    private val content: LinearLayout
    private val cardsContainer: LinearLayout
    private val expandedIds = HashSet<String>()

    init {
        setBackgroundColor(Palette.CANVAS)
        isFillViewport = true
        isVerticalScrollBarEnabled = false

        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(Space.XL), context.dp(Space.L), context.dp(Space.XL), context.dp(Space.HUGE))
        }
        Theme.applyInsets(content, applyTop = true, applyBottom = false)

        content.addView(
            Theme.text(
                context,
                context.getString(R.string.groups_title),
                TypeScale.DISPLAY_SP,
                Palette.TEXT_PRIMARY,
                Theme.LIGHT
            )
        )
        content.addView(
            Theme.text(
                context,
                context.getString(R.string.groups_subtitle),
                TypeScale.BODY_SP,
                Palette.TEXT_SECONDARY
            ).apply {
                setPadding(0, context.dp(Space.XS), 0, context.dp(Space.XL))
                setLineSpacing(context.dp(3).toFloat(), 1f)
            }
        )

        cardsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(cardsContainer)

        content.addView(
            button(context, context.getString(R.string.groups_new), ButtonStyle.SECONDARY) {
                onNewGroup()
            }.apply {
                layoutParams = matchWrap().apply { topMargin = context.dp(Space.S) }
            }
        )

        addView(content)
    }

    fun bind(state: TodayState) {
        val savedScroll = scrollY
        for (child in 0 until cardsContainer.childCount) {
            val card = cardsContainer.getChildAt(child) as? GroupCardView ?: continue
            if (card.isExpanded()) expandedIds.add(card.groupId)
        }
        cardsContainer.removeAllViews()

        val locked = state.lock.isLocked
        for (groupState in state.groups) {
            val card = GroupCardView(
                context,
                groupState.group,
                onToggleEnabled = { enabled -> onToggleGroup(groupState.group, enabled) },
                onEditClicked = { if (locked) onLockedAttempt() else onEditGroup(groupState.group) },
                onManageAppsClicked = { if (locked) onLockedAttempt() else onManageApps(groupState.group) },
                onLockedActionAttempt = onLockedAttempt
            )
            card.bindData(groupState, locked)
            if (groupState.group.id in expandedIds) card.expand()
            cardsContainer.addView(card)
        }

        val isEmpty = state.groups.isEmpty()
        if (isEmpty) {
            cardsContainer.addView(
                Theme.text(
                    context,
                    context.getString(R.string.today_no_groups_body),
                    TypeScale.BODY_SP,
                    Palette.TEXT_MUTED
                ).apply {
                    setPadding(0, context.dp(Space.M), 0, context.dp(Space.M))
                    setLineSpacing(context.dp(3).toFloat(), 1f)
                }
            )
        }
        post { scrollTo(0, savedScroll) }
    }
}
