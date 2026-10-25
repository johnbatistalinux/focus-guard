package com.focusguard.app.ui

import com.focusguard.app.R
import com.focusguard.app.domain.GroupIcon

fun GroupIcon.drawableRes(): Int = when (this) {
    GroupIcon.SOCIAL -> R.drawable.ic_group_social
    GroupIcon.GAMES -> R.drawable.ic_group_games
    GroupIcon.VIDEO -> R.drawable.ic_group_video
    GroupIcon.SHOPPING -> R.drawable.ic_group_shopping
    GroupIcon.NEWS -> R.drawable.ic_group_news
    GroupIcon.CHAT -> R.drawable.ic_group_chat
    GroupIcon.WORK -> R.drawable.ic_group_work
    GroupIcon.STUDY -> R.drawable.ic_group_study
    GroupIcon.MUSIC -> R.drawable.ic_group_music
    GroupIcon.BROWSER -> R.drawable.ic_group_browser
    GroupIcon.CUSTOM -> R.drawable.ic_group_custom
}
