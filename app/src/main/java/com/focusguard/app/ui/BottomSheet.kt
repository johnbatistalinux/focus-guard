package com.focusguard.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Radius
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.matchWrap

/**
 * Native lightweight bottom sheet: warm surface, drag handle, scrollable body,
 * hairline-separated sticky footer and edge-to-edge bottom inset.
 */
open class BottomSheet(context: Context) : Dialog(context, R.style.Theme_FocusGuard_BottomSheet) {

    private lateinit var rootContainer: LinearLayout
    private lateinit var titleTextView: TextView
    private lateinit var subtitleTextView: TextView
    private lateinit var headerLayout: LinearLayout
    private lateinit var bodyContentLayout: LinearLayout
    private lateinit var stickyFooterLayout: LinearLayout
    private lateinit var footerDivider: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        buildSheetLayout()
        setContentView(rootContainer)

        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            @Suppress("DEPRECATION")
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    private fun buildSheetLayout() {
        val density = context.resources.displayMetrics.density
        val screenHeight = context.resources.displayMetrics.heightPixels
        val maxBodyHeight = (screenHeight * 0.66f).toInt()

        val cornerRadius = context.dp(Radius.SHEET).toFloat()
        val topRoundedSurface = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                0f, 0f, 0f, 0f
            )
            setColor(Palette.SURFACE)
        }

        rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = topRoundedSurface
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val dragHandle = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(context.dp(40), context.dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = context.dp(Space.M)
                bottomMargin = context.dp(Space.S)
            }
            background = Theme.box(Palette.ELEVATED, Radius.PILL)
        }
        rootContainer.addView(dragHandle)

        headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(Space.XL), 0, context.dp(Space.XL), context.dp(Space.S))
        }

        titleTextView = Theme.text(
            context,
            "",
            TypeScale.TITLE_L_SP,
            Palette.TEXT_PRIMARY,
            Theme.MEDIUM
        ).apply { visibility = View.GONE }

        subtitleTextView = Theme.text(
            context,
            "",
            TypeScale.CAPTION_SP,
            Palette.TEXT_MUTED
        ).apply {
            setPadding(0, context.dp(Space.XS), 0, 0)
            visibility = View.GONE
        }

        headerLayout.addView(titleTextView)
        headerLayout.addView(subtitleTextView)
        rootContainer.addView(headerLayout)

        bodyContentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(Space.XL), context.dp(Space.XS), context.dp(Space.XL), context.dp(Space.S))
        }

        val bodyScrollView = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val constrainedHeight = MeasureSpec.makeMeasureSpec(maxBodyHeight, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, constrainedHeight)
            }
        }.apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(bodyContentLayout)
        }
        rootContainer.addView(bodyScrollView)

        footerDivider = View(context).apply {
            setBackgroundColor(Palette.HAIRLINE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(1))
            visibility = View.GONE
        }
        rootContainer.addView(footerDivider)

        stickyFooterLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(Space.XL), context.dp(Space.M), context.dp(Space.XL), context.dp(Space.M))
            layoutParams = matchWrap()
        }
        Theme.applyInsets(stickyFooterLayout, applyTop = false, applyBottom = true)
        rootContainer.addView(stickyFooterLayout)
    }

    fun setSheetTitle(title: CharSequence) {
        titleTextView.text = title
        titleTextView.visibility = if (title.isNotBlank()) View.VISIBLE else View.GONE
        updateHeaderVisibility()
    }

    fun setSheetSubtitle(subtitle: CharSequence?) {
        subtitleTextView.text = subtitle
        subtitleTextView.visibility = if (!subtitle.isNullOrBlank()) View.VISIBLE else View.GONE
        updateHeaderVisibility()
    }

    private fun updateHeaderVisibility() {
        if (::headerLayout.isInitialized) {
            headerLayout.visibility = if (
                titleTextView.visibility == View.VISIBLE || subtitleTextView.visibility == View.VISIBLE
            ) View.VISIBLE else View.GONE
        }
    }

    fun setBodyView(view: View) {
        bodyContentLayout.removeAllViews()
        bodyContentLayout.addView(view)
    }

    fun setFooterView(view: View) {
        stickyFooterLayout.removeAllViews()
        stickyFooterLayout.addView(view)
        footerDivider.visibility = View.VISIBLE
    }
}
