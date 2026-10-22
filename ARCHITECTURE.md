# Architectural Contract — FocusGuard

## 1. Stack & Toolchain

| Layer | Technology Choice | Engineering Rationale (4 GB RAM Target) |
| :--- | :--- | :--- |
| **Language** | Kotlin `2.0+` (JVM Target 17) | Strict static typing, null-safety, and zero runtime overhead when avoiding heavy reflection libraries. |
| **Build System** | Gradle (Kotlin DSL) + Android Gradle Plugin (AGP) | Configured with R8/ProGuard (`isMinifyEnabled = true`, `isShrinkResources = true`) to produce a release APK `< 1.5 MB`. |
| **UI Framework** | **100% Android Native Views (`android.view.*` / `android.widget.*`)** | Zero Jetpack Compose, zero heavy `appcompat` or `material` dependencies. Native `View` classes are preloaded in Android's `Zygote` process, resulting in ~0 MB of library RAM overhead. |
| **Overlay Engine** | `WindowManager` + `TYPE_ACCESSIBILITY_OVERLAY` | Eliminates the separate `SYSTEM_ALERT_WINDOW` permission, delivers `< 50 ms` interception latency, and runs directly within the `AccessibilityService` context. |
| **Concurrency / Timers** | `android.os.Handler(Looper.getMainLooper())` + `SystemClock.elapsedRealtime()` | Eliminates `kotlinx-coroutines-android` (~1.5 MB binary + dispatcher allocations). `Handler.postDelayed` on the Main Thread for a 1 Hz tick has negligible CPU and memory cost. |
| **Persistence** | `android.content.SharedPreferences` (Private Mode) + In-Memory Cache | Instantaneous in-memory reads natively backed by Android XML storage without SQLite/Room or DataStore overhead. |
| **Testing** | JUnit 5 (Local JVM Unit Tests for State Machine & Quota Rules) | All decision logic (`FocusPolicyEngine` and `FocusPreferencesStore`) is decoupled from the Android SDK to execute in milliseconds on the local JVM. |

---

## 2. Architecture & Event Flow Diagram

```mermaid
flowchart TD
    classDef os fill:#0f172a,stroke:#38bdf8,stroke-width:2px,color:#f8fafc;
    classDef service fill:#1e1b4b,stroke:#818cf8,stroke-width:2px,color:#f8fafc;
    classDef domain fill:#064e3b,stroke:#34d399,stroke-width:2px,color:#f8fafc;
    classDef ui fill:#4c1d95,stroke:#c084fc,stroke-width:2px,color:#f8fafc;
    classDef storage fill:#701a75,stroke:#f472b6,stroke-width:2px,color:#f8fafc;

    subgraph ANDROID_OS ["Operating System Layer (Android Framework)"]
        A11Y_MGR["⚙️ AccessibilityManager<br/>• Strict packageNames filter<br/>• Dispatches only TYPE_WINDOW_STATE_CHANGED"]:::os
        WIN_MGR["🪟 WindowManager<br/>• TYPE_ACCESSIBILITY_OVERLAY layer"]:::os
        SYS_CLOCK["⏱️ SystemClock.elapsedRealtime()<br/>• Monotonic clock immune to wall-clock tampering"]:::os
    end

    subgraph APP_SERVICE ["Service Layer (Ultra-Light Single Process)"]
        SVC["🛡️ FocusAccessibilityService<br/>• Handles window state events<br/>• Updates serviceInfo.packageNames<br/>• Executes GLOBAL_ACTION_HOME"]:::service
        TICKER["⏲️ MainThreadTicker<br/>• Handler(Looper.getMainLooper())<br/>• 1 Hz tick only during active pause/session"]:::service
    end

    subgraph CORE_DOMAIN ["Pure Domain Layer (100% Kotlin — Zero Android Dependencies)"]
        ENGINE["🧠 FocusPolicyEngine<br/>• Deterministic state machine<br/>• Evaluates daily quota, cooldown, and sessions"]:::domain
        CATALOG["📋 SocialAppCatalog<br/>• Curated immutable package catalog"]:::domain
    end

    subgraph NATIVE_UI ["UI Layer (Pure Android Native Views)"]
        OVERLAY["🛑 OverlayController<br/>• Mindful Pause View (10s+)<br/>• Strict Block View (Cooldown / Quota)<br/>• Floating Pill (Mini-Timer)"]:::ui
        CONFIG_ACT["📱 MainActivity (Configuration)<br/>• Monitored app switches<br/>• Duration selectors & anti-bypass lock status"]:::ui
    end

    subgraph PERSISTENCE ["Persistence Layer"]
        PREFS["💾 FocusPreferencesStore<br/>• SharedPreferences wrapper + RAM cache<br/>• Lazy daily reset by EpochDay"]:::storage
    end

    A11Y_MGR -->|"1. notifies target package window event"| SVC
    SVC -->|"2. queries policy decision"| ENGINE
    ENGINE -->|"3. reads/writes usage & rewards"| PREFS
    ENGINE -->|"4. reads monotonic clock"| SYS_CLOCK
    SVC -->|"5. commands overlay state"| OVERLAY
    OVERLAY -->|"6. attaches/detaches native Views"| WIN_MGR
    SVC -->|"7. starts/stops 1 Hz ticker"| TICKER
    TICKER -->|"8. updates countdown seconds"| OVERLAY
    TICKER -->|"9. notifies session expiration"| SVC
    CONFIG_ACT -->|"reads fixed catalog"| CATALOG
    CONFIG_ACT -->|"saves config (when unlocked)"| PREFS
    PREFS -.->|"notifies package filter update"| SVC
```

---

## 3. Intervention State Machine (`FocusPolicyEngine`)

```mermaid
stateDiagram-v2
    [*] --> IDLE : No monitored app in foreground

    IDLE --> HARD_BLOCKED : Target app opened AND (Daily Quota <= 0 OR In Cooldown)
    IDLE --> MINDFUL_PAUSE : Target app opened AND (Daily Quota > 0 AND No Cooldown)

    HARD_BLOCKED --> IDLE : User taps "Return to Home" (GLOBAL_ACTION_HOME)

    MINDFUL_PAUSE --> IDLE : User resists impulse ("Close app" -> GLOBAL_ACTION_HOME)
    MINDFUL_PAUSE --> MINDFUL_PAUSE : 1s Tick (N pause seconds remaining)
    MINDFUL_PAUSE --> ACTIVE_SESSION : Pause completed (0s) AND user starts Quick (1m) or Standard Session

    ACTIVE_SESSION --> SESSION_PAUSED : User exited to Home/other app or turned screen off
    SESSION_PAUSED --> ACTIVE_SESSION : User returned to same app within grace window
    SESSION_PAUSED --> IDLE : Grace window expired in background (credits early exit bonus)

    ACTIVE_SESSION --> COOLDOWN_TRIGGERED : Session timer reached 00:00 OR Daily Quota exhausted
    COOLDOWN_TRIGGERED --> IDLE : Executes GLOBAL_ACTION_HOME + Records Cooldown timestamp
```

---

## 4. Directory Layout & Modular Boundaries

The project enforces strict separation within a single root package and 4 cohesive subpackages:

- `com.focusguard.app/`
  - `domain/` *(100% pure Kotlin, JVM-testable without an emulator)*
    - `SocialAppCatalog.kt` — Immutable catalog of supported social media apps (`TargetApp`) and lookup maps.
    - `FocusModels.kt` — Core models (`FocusConfig`, `AppUsageState`, `FocusRewardState`, `FocusRewardSummary`, `BlockReason`, `InterventionAction`).
    - `FocusPolicyEngine.kt` — Pure deterministic state machine (Progressive Pause, 1 Hz Breathing Guide, 1-min Micro-Session, Multi-App Paused Session Map, Voluntary Early-Exit Bonus, and Bitmask Consistency).
    - `TimeProvider.kt` — Monotonic clock and local calendar abstraction (`TimeProvider`) plus the `FocusPolicyStore` persistence contract.
  - `data/`
    - `KeyValueBackend.kt` — Minimal storage abstraction (`KeyValueBackend`, `SharedPreferencesBackend`) and `FocusRuntime` process-wide singleton providing shared `FocusPreferencesStore` and `FocusPolicyEngine` instances across `MainActivity` and `FocusAccessibilityService`.
    - `FocusPreferencesStore.kt` — Ultra-light `SharedPreferences` implementation with in-memory caching, atomic batch commits, lazy daily reset, and `consistencyBitmask` rollover.
  - `service/`
    - `FocusAccessibilityService.kt` — Android `AccessibilityService` managing lifecycle, dynamic `packageNames` filtering, interactive window & IME detection, screen-off/unlock handling, and `GLOBAL_ACTION_HOME`.
    - `OverlayController.kt` — Programmatic native `View` manager over `WindowManager` (`TYPE_ACCESSIBILITY_OVERLAY`) with cached drawables, dynamic `FLAG_KEEP_SCREEN_ON`, haptic celebration, micro-session button, and interactive floating pill (tap to swap corner, long-press to finish early with bonus).
    - `MainThreadTicker.kt` — Zero-coroutine 1 Hz countdown wrapper around `Handler(Looper.getMainLooper())`.
  - `ui/`
    - `MainActivity.kt` — Pure `android.app.Activity` configuration screen featuring the Bento dashboard, group cards, and anti-bypass lock enforcement.
    - `InstalledAppScanner.kt` — On-demand scanner querying `PackageManager` for launchable user apps without background overhead.
    - `AppPickerDialog.kt` — Programmatic dark-themed modal dialog with real-time search for assigning installed apps to focus groups.
    - `GroupConfigDialog.kt` — Programmatic modal dialog for editing category parameters (emoji, presets, shared quotas, session timers, cooldown).

---

## 5. Core Domain Contracts (`domain`)

```kotlin
package com.focusguard.app.domain

data class FocusGroup(
    val id: String,
    val name: String,
    val iconEmoji: String = "📱",
    val enabled: Boolean = true,
    val appIds: Set<String> = emptySet(),
    val mindfulPauseSeconds: Int = 10,
    val sessionDurationMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val dailyQuotaMinutes: Int = 30,
    val progressivePauseEnabled: Boolean = true,
    val sharedQuota: Boolean = true
) {
    val sessionDurationSeconds: Long get() = sessionDurationMinutes * 60L
    val cooldownSeconds: Long get() = cooldownMinutes * 60L
    val dailyQuotaSeconds: Long get() = dailyQuotaMinutes * 60L
}

data class TargetApp(
    val id: String,
    val displayName: String,
    val packageNames: Set<String>,
    val brandColorHex: String = "#38BDF8",
    val isCustom: Boolean = false
)

data class FocusConfig(
    val enabledAppIds: Set<String> = setOf("instagram", "tiktok"),
    val mindfulPauseSeconds: Int = 10,
    val sessionDurationMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val dailyQuotaMinutes: Int = 30,
    val progressivePauseEnabled: Boolean = true,
    val selectedAnchorHabit: String = DEFAULT_ANCHOR_HABIT,
    val groups: List<FocusGroup> = emptyList(),
    val customApps: List<TargetApp> = emptyList(),
    val sharedQuotaEnabled: Boolean = true
)

sealed interface InterventionAction {
    /** Ignored package or return to Launcher; hides any active overlay. */
    data object DismissOverlay : InterventionAction

    /** Irrelevant transient window event (e.g. soft keyboard / popup over active session); preserves state. */
    data object NoOp : InterventionAction

    /** Immediately closes the target app via GLOBAL_ACTION_HOME. */
    data class KickToHome(
        val appId: String,
        val reason: BlockReason
    ) : InterventionAction

    /** Strict block screen when cooldown is active or daily quota is exhausted. */
    data class ShowHardBlock(
        val appId: String,
        val displayName: String,
        val reason: BlockReason,
        val remainingBlockSeconds: Long,
        val groupName: String = ""
    ) : InterventionAction

    /** Mindful breathing/pause barrier before unlocking a session. */
    data class ShowMindfulPause(
        val appId: String,
        val displayName: String,
        val remainingPauseSeconds: Int,
        val readyToStartSession: Boolean,
        val remainingDailyQuotaSeconds: Long,
        val breathingCue: String = "",
        val anchorActionLabel: String = "Fechar e voltar ao que importa",
        val rewardBannerText: String = "",
        val reclaimedMinutesOnAbort: Int = 5,
        val allowQuickMicroSession: Boolean = false,
        val groupName: String = ""
    ) : InterventionAction

    /** Active unlocked session: compact floating pill. */
    data class ShowFloatingTimer(
        val appId: String,
        val displayName: String,
        val remainingSessionSeconds: Long,
        val remainingDailyQuotaSeconds: Long,
        val groupName: String = ""
    ) : InterventionAction
}
        val remainingPauseSeconds: Int,
        val readyToStartSession: Boolean,
        val remainingDailyQuotaSeconds: Long,
        val breathingCue: String = "",
        val anchorActionLabel: String = "Fechar e voltar ao que importa",
        val rewardBannerText: String = "",
        val reclaimedMinutesOnAbort: Int = 5,
        val allowQuickMicroSession: Boolean = false
    ) : InterventionAction

    /** Active unlocked session with discrete floating timer pill. */
    data class ShowFloatingTimer(
        val appId: String,
        val displayName: String,
        val remainingSessionSeconds: Long,
        val remainingDailyQuotaSeconds: Long
    ) : InterventionAction
}

enum class BlockReason {
    COOLDOWN_ACTIVE,
    DAILY_QUOTA_EXCEEDED
}
```

---

## 6. Extreme Optimization Strategies for 4 GB RAM Devices

1. **Zero UI Library Dependencies (`androidx.appcompat` & `com.google.android.material` excluded):**
   - `MainActivity` inherits directly from `android.app.Activity` using the native system theme (`android:Theme.DeviceDefault.NoActionBar`).
   - Saves ~8 MB of DEX memory in RAM and keeps cold activity startup under `< 40 ms`.
2. **Transient Window Filtering (Anti-Event-Spam):**
   - Virtual keyboards (`Gboard`, `Samsung Honeyboard`, `SwiftKey`) and system overlays (`SystemUI`, `PermissionController`) emit `TYPE_WINDOW_STATE_CHANGED` while the user is typing inside a monitored app.
   - `FocusAccessibilityService` ignores transient IME/system packages and non-interactive screen states (`PowerManager.isInteractive == false`) so active sessions are neither falsely reset nor drained while the screen is off.
3. **Dynamic OS-Level Package Filtering:**
   - When idle, `serviceInfo.packageNames` is restricted solely to `enabledPackages`, keeping FocusGuard at `0.0%` CPU during normal phone usage.
   - Broad window-transition listening (`packageNames = null`) is activated strictly while a session or pause barrier is live in order to detect home/app exits immediately without background polling.
