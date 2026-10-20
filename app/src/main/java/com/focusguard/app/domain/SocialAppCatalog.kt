package com.focusguard.app.domain

/**
 * Representa uma rede social suportada no catálogo fixo do FocusGuard.
 * Evita a necessidade da permissão invasiva QUERY_ALL_PACKAGES e reduz uso de RAM.
 */
data class TargetApp(
    val id: String,
    val displayName: String,
    val packageNames: Set<String>,
    val brandColorHex: String = "#38BDF8"
)

/**
 * Catálogo imutável das principais redes sociais causadoras de deep scrolling.
 */
object SocialAppCatalog {

    val SUPPORTED_APPS: List<TargetApp> = listOf(
        TargetApp(
            id = "instagram",
            displayName = "Instagram",
            packageNames = setOf("com.instagram.android", "com.instagram.lite"),
            brandColorHex = "#E1306C"
        ),
        TargetApp(
            id = "tiktok",
            displayName = "TikTok",
            packageNames = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"),
            brandColorHex = "#00F2FE"
        ),
        TargetApp(
            id = "youtube",
            displayName = "YouTube",
            packageNames = setOf("com.google.android.youtube"),
            brandColorHex = "#FF0000"
        ),
        TargetApp(
            id = "twitter_x",
            displayName = "X (Twitter)",
            packageNames = setOf("com.twitter.android", "com.twitter.android.lite"),
            brandColorHex = "#94A3B8"
        ),
        TargetApp(
            id = "reddit",
            displayName = "Reddit",
            packageNames = setOf("com.reddit.frontpage"),
            brandColorHex = "#FF4500"
        ),
        TargetApp(
            id = "threads",
            displayName = "Threads",
            packageNames = setOf("com.instagram.barcelona"),
            brandColorHex = "#A855F7"
        ),
        TargetApp(
            id = "facebook",
            displayName = "Facebook",
            packageNames = setOf("com.facebook.katana", "com.facebook.lite"),
            brandColorHex = "#1877F2"
        ),
        TargetApp(
            id = "kwai",
            displayName = "Kwai",
            packageNames = setOf("com.kwai.video"),
            brandColorHex = "#FF8C00"
        )
    )

    private val packageToAppMap: Map<String, TargetApp> = buildMap {
        for (app in SUPPORTED_APPS) {
            for (pkg in app.packageNames) {
                put(pkg, app)
            }
        }
    }

    /**
     * Busca o [TargetApp] correspondente a um nome de pacote do Android em O(1).
     */
    fun findByPackageName(packageName: String): TargetApp? = packageToAppMap[packageName]

    /**
     * Converte os IDs ou pacotes habilitados na lista completa de pacotes Android para filtro do sistema.
     */
    fun resolveAndroidPackages(enabledAppIds: Set<String>): Set<String> {
        val result = HashSet<String>()
        for (app in SUPPORTED_APPS) {
            if (app.id in enabledAppIds) {
                result.addAll(app.packageNames)
            }
        }
        return result
    }
}
