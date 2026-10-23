package com.focusguard.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.focusguard.app.R

/**
 * Native lightweight bottom sheet dialog with drag handle, scrollable body,
 * and sticky action footer. Zero external UI dependencies.
 */
open class BottomSheet(context: Context) : Dialog(context, R.style.Theme_FocusGuard_BottomSheet) {

    private lateinit var rootContainer: LinearLayout
    private lateinit var titleTextView: TextView
    private lateinit var subtitleTextView: TextView
    private lateinit var bodyScrollView: ScrollView
    private lateinit var bodyContentLayout: LinearLayout
    private lateinit var stickyFooterLayout: LinearLayout

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
            // Ensure window allows drawing over nav bar for insets
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }
    }

    private fun buildSheetLayout() {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density + 0.5f).toInt()
        val screenHeight = context.resources.displayMetrics.heightPixels
        val maxBodyHeight = (screenHeight * 0.65f).toInt()

        // 1. Root Container with top-rounded surface
        val cornerRadius = dp(20).toFloat()
        val topRoundedSurface = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                0f, 0f, 0f, 0f
            )
            setColor(FocusTheme.COLOR_SURFACE)
        }

        rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = topRoundedSurface
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 2. Drag Handle
        val dragHandle = View(context).apply {
            val handleWidth = dp(40)
            val handleHeight = dp(4)
            val lp = LinearLayout.LayoutParams(handleWidth, handleHeight).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = dp(2).toFloat()
                setColor(FocusTheme.COLOR_ELEVATED)
            }
        }
        rootContainer.addView(dragHandle)

        // 3. Header: Title and optional subtitle
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(20)
            setPadding(padH, 0, padH, dp(8))
        }

        titleTextView = TextView(context).apply {
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }

        subtitleTextView = TextView(context).apply {
            setTextColor(FocusTheme.COLOR_TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
            visibility = View.GONE
        }

        headerLayout.addView(titleTextView)
        headerLayout.addView(subtitleTextView)
        rootContainer.addView(headerLayout)

        // 4. Scrollable Body
        bodyContentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(20)
            setPadding(padH, dp(4), padH, dp(8))
        }

        bodyScrollView = object : ScrollView(context) {
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

        // 5. Sticky Footer
        stickyFooterLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(20)
            val padV = dp(14)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        FocusTheme.applySystemWindowInsetsPadding(
            stickyFooterLayout,
            applyTop = false,
            applyBottom = true
        )
        rootContainer.addView(stickyFooterLayout)
    }

    fun setSheetTitle(title: CharSequence) {
        titleTextView.text = title
        titleTextView.visibility = if (title.isNotBlank()) View.VISIBLE else View.GONE
    }

    fun setSheetSubtitle(subtitle: CharSequence?) {
        subtitleTextView.text = subtitle
        subtitleTextView.visibility = if (!subtitle.isNullOrBlank()) View.VISIBLE else View.GONE
    }

    fun setBodyView(view: View) {
        bodyContentLayout.removeAllViews()
        bodyContentLayout.addView(view)
    }

    fun addBodyView(view: View, params: LinearLayout.LayoutParams? = null) {
        if (params != null) {
            bodyContentLayout.addView(view, params)
        } else {
            bodyContentLayout.addView(view)
        }
    }

    fun setFooterView(view: View) {
        stickyFooterLayout.removeAllViews()
        stickyFooterLayout.addView(view)
    }

    fun getBodyContainer(): LinearLayout = bodyContentLayout

    fun getFooterContainer(): LinearLayout = stickyFooterLayout
}
