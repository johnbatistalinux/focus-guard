package com.focusguard.app.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.TargetApp
import com.focusguard.app.ui.design.ButtonStyle
import com.focusguard.app.ui.design.Palette
import com.focusguard.app.ui.design.Space
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.TypeScale
import com.focusguard.app.ui.design.button
import com.focusguard.app.ui.design.dp
import com.focusguard.app.ui.design.inputField
import com.focusguard.app.ui.design.matchWrap
import com.focusguard.app.ui.design.monogram
import com.focusguard.app.ui.design.sectionHeader
import com.focusguard.app.ui.design.themedCheckBox
import java.util.concurrent.Executors
import com.focusguard.app.ui.design.hapticLight

/**
 * Native bottom sheet for filtering and selecting the apps a group should monitor.
 * Scans installed launchers on a single background thread and renders selected,
 * suggested and remaining sections with a sticky confirm footer.
 */
class AppPickerSheet(
    context: Context,
    private val group: FocusGroup,
    private val config: FocusConfig,
    private val onAppsConfirmed: (selectedAppIds: Set<String>, allCustomApps: List<TargetApp>) -> Unit
) : BottomSheet(context) {

    private var allApps: List<TargetApp> = emptyList()
    private var isLoading: Boolean = true
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchDebounceRunnable: Runnable? = null
    private val selectedIds: MutableSet<String> = group.appIds.toMutableSet()
    private val otherGroupsByAppId: MutableMap<String, FocusGroup> = HashMap()

    private lateinit var searchInput: EditText
    private lateinit var listContainer: LinearLayout
    private lateinit var confirmButton: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSheetTitle(context.getString(R.string.picker_title, group.name))
        setSheetSubtitle(context.getString(R.string.picker_subtitle))

        for (otherGroup in config.getEffectiveGroups()) {
            if (otherGroup.id != group.id) {
                for (appId in otherGroup.appIds) otherGroupsByAppId[appId] = otherGroup
            }
        }

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        searchInput = inputField(context, context.getString(R.string.picker_search_hint)).apply {
            layoutParams = matchWrap().apply { bottomMargin = context.dp(Space.S) }
        }
        body.addView(searchInput)

        listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(listContainer)
        setBodyView(body)

        confirmButton = button(context, context.getString(R.string.picker_confirm, selectedIds.size), ButtonStyle.PRIMARY) {
            val customAppsMap = config.customApps.associateBy { it.id }.toMutableMap()
            for (app in allApps) {
                if (app.isCustom && app.id in selectedIds) {
                    customAppsMap[app.id] = app
                }
            }
            onAppsConfirmed(selectedIds, customAppsMap.values.toList())
            dismiss()
        }.apply { isEnabled = false }
        setFooterView(confirmButton)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
                val query = s?.toString().orEmpty()
                val runnable = Runnable {
                    if (!isLoading) renderApps(query)
                }
                searchDebounceRunnable = runnable
                mainHandler.postDelayed(runnable, 200L)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        listContainer.addView(loadingRow())

        val appContext = context.applicationContext
        backgroundExecutor.execute {
            val scanned = InstalledAppScanner.scanLaunchableApps(appContext, config.customApps)
            mainHandler.post {
                if (isShowing) {
                    allApps = scanned
                    isLoading = false
                    confirmButton.isEnabled = true
                    renderApps(searchInput.text?.toString().orEmpty())
                }
            }
        }
    }

    override fun onStop() {
        searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
        backgroundExecutor.shutdownNow()
        super.onStop()
    }

    private fun renderApps(query: String) {
        listContainer.removeAllViews()
        val cleanQuery = query.trim().lowercase()

        if (cleanQuery.isNotEmpty()) {
            val filtered = allApps.filter {
                it.displayName.lowercase().contains(cleanQuery) ||
                    it.packageNames.any { pkg -> pkg.lowercase().contains(cleanQuery) }
            }
            if (filtered.isEmpty()) {
                listContainer.addView(placeholder(context.getString(R.string.picker_empty)))
            } else {
                for (app in filtered) listContainer.addView(appRow(app))
            }
            return
        }

        val selectedSection = allApps.filter { it.id in selectedIds }
        val suggestedSection = allApps.filter { !it.isCustom && it.id !in selectedIds }
        val otherSection = allApps.filter { it.isCustom && it.id !in selectedIds }

        if (selectedSection.isNotEmpty()) {
            listContainer.addView(
                sectionHeader(
                    context,
                    context.getString(R.string.picker_section_selected, selectedSection.size)
                )
            )
            for (app in selectedSection) listContainer.addView(appRow(app))
        }
        if (suggestedSection.isNotEmpty()) {
            listContainer.addView(sectionHeader(context, context.getString(R.string.picker_section_suggested)))
            for (app in suggestedSection) listContainer.addView(appRow(app))
        }
        if (otherSection.isNotEmpty()) {
            listContainer.addView(sectionHeader(context, context.getString(R.string.picker_section_all)))
            for (app in otherSection) listContainer.addView(appRow(app))
        }
    }

    private fun loadingRow(): TextView = placeholder(context.getString(R.string.picker_loading))

    private fun placeholder(text: CharSequence): TextView = Theme.text(
        context,
        text,
        TypeScale.CAPTION_SP,
        Palette.TEXT_MUTED,
        gravity = Gravity.CENTER
    ).apply {
        setPadding(0, context.dp(Space.XXL), 0, context.dp(Space.XXL))
    }

    private fun appRow(app: TargetApp): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(56)
            setPadding(context.dp(Space.XS), context.dp(Space.S), context.dp(Space.XS), context.dp(Space.S))
            isClickable = true
            isFocusable = true
        }

        row.addView(
            monogram(context, app.displayName, app.brandColorHex, sizeDp = 34).apply {
                layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                    marginEnd = context.dp(Space.M)
                }
            }
        )

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(
            Theme.text(
                context,
                app.displayName,
                TypeScale.BODY_SP,
                Palette.TEXT_PRIMARY,
                Theme.MEDIUM,
                maxLines = 1
            ).apply { ellipsize = TextUtils.TruncateAt.END }
        )

        val otherGroup = otherGroupsByAppId[app.id]
        val subtitle = if (otherGroup != null) {
            context.getString(R.string.picker_moved_from, otherGroup.name)
        } else {
            app.packageNames.firstOrNull().orEmpty()
        }
        textCol.addView(
            Theme.text(
                context,
                subtitle,
                TypeScale.CAPTION_SP,
                if (otherGroup != null) Palette.WARN else Palette.TEXT_MUTED,
                maxLines = 1
            ).apply {
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, context.dp(2), 0, 0)
            }
        )
        row.addView(textCol)

        val checkBox = themedCheckBox(context, app.id in selectedIds)
        row.addView(checkBox)

        row.setOnClickListener { view ->
            view.hapticLight()
            if (app.id in selectedIds) {
                selectedIds.remove(app.id)
                checkBox.isChecked = false
            } else {
                selectedIds.add(app.id)
                checkBox.isChecked = true
            }
            confirmButton.text = context.getString(R.string.picker_confirm, selectedIds.size)
        }

        return row
    }
}
