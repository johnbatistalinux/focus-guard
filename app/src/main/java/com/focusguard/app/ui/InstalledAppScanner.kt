package com.focusguard.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.focusguard.app.domain.SocialAppCatalog
import com.focusguard.app.domain.TargetApp

/**
 * Scanner leve sob demanda de aplicativos instalados no dispositivo.
 *
 * Utiliza a declaração <queries> com Intent.ACTION_MAIN + CATEGORY_LAUNCHER,
 * eliminando a necessidade da permissão invasiva QUERY_ALL_PACKAGES e mantendo
 * 0% de uso de CPU e memória em background.
 */
object InstalledAppScanner {

    fun scanLaunchableApps(context: Context, customApps: Collection<TargetApp> = emptyList()): List<TargetApp> {
        val pm = context.packageManager
        val myPackageName = context.packageName

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfoList = runCatching {
            pm.queryIntentActivities(launcherIntent, 0)
        }.getOrDefault(emptyList())

        val customByPkg = customApps.flatMap { app -> app.packageNames.map { it to app } }.toMap()
        val scannedApps = HashMap<String, TargetApp>(resolveInfoList.size)

        for (resolveInfo in resolveInfoList) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            if (pkg == myPackageName) continue

            // 1. Verifica se já pertence ao catálogo de redes sociais nativo
            val builtin = SocialAppCatalog.findByPackageName(pkg)
            if (builtin != null) {
                scannedApps[builtin.id] = builtin
                continue
            }

            // 2. Verifica se já é um customApp existente
            val existingCustom = customByPkg[pkg]
            if (existingCustom != null) {
                scannedApps[existingCustom.id] = existingCustom
                continue
            }

            // 3. App instalado não mapeado: cria nova entrada TargetApp customizada
            val label = runCatching {
                resolveInfo.loadLabel(pm).toString()
            }.getOrDefault(pkg)

            val appId = "custom_${pkg.replace('.', '_')}"
            scannedApps[appId] = TargetApp(
                id = appId,
                displayName = label,
                packageNames = setOf(pkg),
                brandColorHex = "#38BDF8",
                isCustom = true
            )
        }

        // Garante que todas as redes sociais do catálogo padrão estejam disponíveis mesmo se não instaladas
        for (app in SocialAppCatalog.SUPPORTED_APPS) {
            if (!scannedApps.containsKey(app.id)) {
                scannedApps[app.id] = app
            }
        }

        return scannedApps.values.sortedBy { it.displayName.lowercase() }
    }
}
