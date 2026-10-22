package com.focusguard.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.TargetApp

/**
 * Diálogo nativo escuro para busca e seleção dinâmica de aplicativos em um FocusGroup.
 */
class AppPickerDialog(
    private val context: Context,
    private val group: FocusGroup,
    private val config: FocusConfig,
    private val onAppsConfirmed: (selectedAppIds: Set<String>, allCustomApps: List<TargetApp>) -> Unit
) {

    fun show() {
        val allApps = InstalledAppScanner.scanLaunchableApps(context, config.customApps)
        val selectedIds = group.appIds.toMutableSet()
        val otherGroupsByAppId = HashMap<String, FocusGroup>()

        for (g in config.getEffectiveGroups()) {
            if (g.id != group.id) {
                for (appId in g.appIds) {
                    otherGroupsByAppId[appId] = g
                }
            }
        }

        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density + 0.5f).toInt()

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F19"))
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val titleView = TextView(context).apply {
            text = "Aplicativos em ${group.iconEmoji} ${group.name}"
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }

        val subtitleView = TextView(context).apply {
            text = "Selecione os aplicativos gerenciados por esta categoria."
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(12))
        }

        val searchInput = EditText(context).apply {
            hint = "🔍 Buscar aplicativo ou jogo..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(340)
            ).apply {
                topMargin = dp(12)
            }
        }

        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(listContainer)

        fun renderRows(filterQuery: String) {
            listContainer.removeAllViews()
            val query = filterQuery.trim().lowercase()
            val filtered = allApps.filter {
                query.isEmpty() ||
                    it.displayName.lowercase().contains(query) ||
                    it.packageNames.any { pkg -> pkg.lowercase().contains(query) }
            }

            if (filtered.isEmpty()) {
                val emptyView = TextView(context).apply {
                    text = "Nenhum aplicativo encontrado."
                    setTextColor(Color.parseColor("#64748B"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(24), 0, dp(24))
                }
                listContainer.addView(emptyView)
                return
            }

            for (app in filtered) {
                val otherGroup = otherGroupsByAppId[app.id]
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, dp(8))
                }

                val checkBox = CheckBox(context).apply {
                    isChecked = app.id in selectedIds
                    buttonTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedIds.add(app.id)
                        } else {
                            selectedIds.remove(app.id)
                        }
                    }
                }

                val textColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginStart = dp(8)
                    }
                }

                val appName = TextView(context).apply {
                    text = app.displayName
                    setTextColor(Color.parseColor("#F8FAFC"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.DEFAULT_BOLD
                }

                val infoSubtitle = TextView(context).apply {
                    if (otherGroup != null) {
                        text = "Atualmente em: ${otherGroup.iconEmoji} ${otherGroup.name}"
                        setTextColor(Color.parseColor("#F59E0B"))
                    } else {
                        text = app.packageNames.firstOrNull().orEmpty()
                        setTextColor(Color.parseColor("#64748B"))
                    }
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                }

                textColumn.addView(appName)
                textColumn.addView(infoSubtitle)

                row.addView(checkBox)
                row.addView(textColumn)
                row.setOnClickListener {
                    checkBox.isChecked = !checkBox.isChecked
                }

                listContainer.addView(row)
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderRows(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        renderRows("")

        rootLayout.addView(titleView)
        rootLayout.addView(subtitleView)
        rootLayout.addView(searchInput)
        rootLayout.addView(scrollView)

        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(rootLayout)
            .setPositiveButton("Salvar") { _, _ ->
                // Mescla os customApps selecionados com os customApps já conhecidos
                val customAppsMap = config.customApps.associateBy { it.id }.toMutableMap()
                for (app in allApps) {
                    if (app.isCustom && app.id in selectedIds) {
                        customAppsMap[app.id] = app
                    }
                }
                onAppsConfirmed(selectedIds, customAppsMap.values.toList())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
