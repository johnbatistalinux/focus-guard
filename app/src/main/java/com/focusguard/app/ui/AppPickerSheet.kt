package com.focusguard.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.TargetApp
import com.focusguard.app.ui.FocusTheme.dp
import com.focusguard.app.ui.FocusTheme.performConfirmHaptic
import com.focusguard.app.ui.FocusTheme.performLightHaptic

/**
 * Native bottom sheet for filtering and selecting apps for a focus group (< 250 lines).
 * Features categorized sections, 56dp touch targets, brand dots, and a sticky confirm footer.
 */
class AppPickerSheet(
    context: Context,
    private val group: FocusGroup,
    private val config: FocusConfig,
    private val onAppsConfirmed: (selectedAppIds: Set<String>, allCustomApps: List<TargetApp>) -> Unit
) : BottomSheet(context) {

    private val allApps: List<TargetApp> by lazy {
        InstalledAppScanner.scanLaunchableApps(context, config.customApps)
    }
    private val selectedIds: MutableSet<String> = group.appIds.toMutableSet()
    private val otherGroupsByAppId: MutableMap<String, FocusGroup> = HashMap()

    private lateinit var searchInput: EditText
    private lateinit var listContainer: LinearLayout
    private lateinit var confirmButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSheetTitle("Aplicativos em ${group.iconEmoji} ${group.name}")
        setSheetSubtitle("Selecione os aplicativos gerenciados por esta categoria.")

        for (g in config.getEffectiveGroups()) {
            if (g.id != group.id) {
                for (appId in g.appIds) otherGroupsByAppId[appId] = g
            }
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        searchInput = EditText(context).apply {
            hint = "🔍 Buscar aplicativo ou jogo..."
            setHintTextColor(FocusTheme.COLOR_TEXT_MUTED)
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply {
                setColor(FocusTheme.COLOR_ELEVATED)
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        body.addView(searchInput)

        listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(listContainer)
        setBodyView(body)

        // Sticky Footer
        val density = context.resources.displayMetrics.density
        confirmButton = Button(context).apply {
            text = "Confirmar (${selectedIds.size})"
            setTextColor(FocusTheme.COLOR_ACCENT_TEXT_DARK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            background = FocusTheme.flatRipple(FocusTheme.COLOR_ACCENT, cornerDp = 12, density = density)
            minHeight = dp(52)
            setOnClickListener { view ->
                view.performConfirmHaptic()
                val customAppsMap = config.customApps.associateBy { it.id }.toMutableMap()
                for (app in allApps) {
                    if (app.isCustom && app.id in selectedIds) {
                        customAppsMap[app.id] = app
                    }
                }
                onAppsConfirmed(selectedIds, customAppsMap.values.toList())
                dismiss()
            }
        }
        setFooterView(confirmButton)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        renderApps("")
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
                listContainer.addView(createEmptyView())
            } else {
                for (app in filtered) listContainer.addView(createAppRow(app))
            }
            return
        }

        // Partition into 3 sections when not searching
        val selectedSection = allApps.filter { it.id in selectedIds }
        val suggestedSection = allApps.filter { !it.isCustom && it.id !in selectedIds }
        val allOtherSection = allApps.filter { it.isCustom && it.id !in selectedIds }

        if (selectedSection.isNotEmpty()) {
            listContainer.addView(createSectionHeader("Selecionados (${selectedSection.size})"))
            for (app in selectedSection) listContainer.addView(createAppRow(app))
        }
        if (suggestedSection.isNotEmpty()) {
            listContainer.addView(createSectionHeader("Sugeridos"))
            for (app in suggestedSection) listContainer.addView(createAppRow(app))
        }
        if (allOtherSection.isNotEmpty()) {
            listContainer.addView(createSectionHeader("Todos os Aplicativos"))
            for (app in allOtherSection) listContainer.addView(createAppRow(app))
        }
    }

    private fun createSectionHeader(title: String): TextView = TextView(context).apply {
        text = title.uppercase(); setTextColor(FocusTheme.COLOR_TEXT_MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(14), 0, dp(6))
    }

    private fun createEmptyView(): TextView = TextView(context).apply {
        text = "Nenhum aplicativo encontrado."; setTextColor(FocusTheme.COLOR_TEXT_MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); gravity = Gravity.CENTER
        setPadding(0, dp(24), 0, dp(24))
    }

    private fun createAppRow(app: TargetApp): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            isClickable = true
            isFocusable = true
        }

        // Brand Dot
        val dotColor = runCatching { Color.parseColor(app.brandColorHex) }
            .getOrDefault(FocusTheme.COLOR_ACCENT)
        val brandDot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                marginEnd = dp(12)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
        }
        row.addView(brandDot)

        // Labels
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(context).apply {
            text = app.displayName
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val otherGroup = otherGroupsByAppId[app.id]
        val subtitleView = TextView(context).apply {
            if (otherGroup != null) {
                text = "Será movido de ${otherGroup.iconEmoji} ${otherGroup.name}"
                setTextColor(FocusTheme.COLOR_WARN)
            } else {
                text = app.packageNames.firstOrNull().orEmpty()
                setTextColor(FocusTheme.COLOR_TEXT_MUTED)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        textCol.addView(nameView)
        textCol.addView(subtitleView)
        row.addView(textCol)

        // CheckBox
        val checkBox = CheckBox(context).apply {
            isChecked = app.id in selectedIds
            buttonTintList = ColorStateList.valueOf(FocusTheme.COLOR_ACCENT)
            isClickable = false
        }
        row.addView(checkBox)

        row.setOnClickListener { view ->
            view.performLightHaptic()
            if (app.id in selectedIds) {
                selectedIds.remove(app.id)
                checkBox.isChecked = false
            } else {
                selectedIds.add(app.id)
                checkBox.isChecked = true
            }
            confirmButton.text = "Confirmar (${selectedIds.size})"
        }

        return row
    }
}
