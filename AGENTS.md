# AGENTS.md — FocusGuard Onboarding & Routing Compass

> **Purpose:** High-leverage architectural compass and file routing index for AI coding agents operating within constrained context windows. Read this file **first** before searching or reading source files.

---

## 1. System Overview & Core Philosophy

**FocusGuard** is an ultra-lightweight native Android digital wellbeing and app-locking interceptor designed to break automatic doomscrolling habits through mindful friction barriers (`BarrierView`), shared category quotas (`FocusGroup`), post-session cooldowns, and a floating session countdown pill (`FloatingPillView`).

### Guiding Principles
- **Ultra-Lightweight & Zero Bloat:** Engineered for 4 GB RAM devices (`< 15 MB` RAM footprint, `0.0%` idle CPU, release APK `< 1.5 MB`). Zero third-party runtime libraries, zero ads, zero telemetry, and no `INTERNET` permission.
- **100% Event-Driven:** Uses `AccessibilityService` with dynamic OS-level `packageNames` filtering and `TYPE_ACCESSIBILITY_OVERLAY` (`< 50 ms` interception latency, no background polling, no `SYSTEM_ALERT_WINDOW` permission).
- **Strict Anti-Bypass Integrity:** When any monitored app or group is in active cooldown or has exhausted its daily quota, configuration mutations enter a strict read-only lock (`isStrictLockActive`).

---

## 2. Non-Negotiable Stack Invariants

### UI Layer: Pure Programmatic Android Kotlin Views
- **NEVER use Jetpack Compose:** Do not add `@Composable` annotations, Compose compiler plugins, or `androidx.compose.*` dependencies.
- **NEVER create XML layout files:** Do not create or inflate layout files in `app/src/main/res/layout/`. All views, screens, cards, and bottom sheets are constructed programmatically in Kotlin using `android.view.*` and `android.widget.*`.
- **Zero Heavy UI Frameworks:** Activities inherit directly from `android.app.Activity` (`Theme.DeviceDefault.NoActionBar`). Do not introduce `androidx.appcompat`, `com.google.android.material`, `ConstraintLayout`, or navigation libraries.

### Design System: "Warm Dusk" (`com.focusguard.app.ui.design`)
- **Single Source of Truth:** All colors, typography, spacing, corner radii, motion durations, and UI widgets **MUST** derive from `com.focusguard.app.ui.design`:
  - `Theme.kt`: `Palette` (`CANVAS`, `SURFACE`, `ELEVATED`, `SUNKEN`, `HAIRLINE`, `ACCENT`, `SAGE`, `WARN`, `DANGER`), `TypeScale`, `Space` (8pt grid), `Radius`, `Motion`, `Theme.dp()`, `Theme.applyInsets()`, haptics (`hapticLight()`, `hapticConfirm()`), and `Theme.formatClock()`.
  - `UiKit.kt`: Reusable primitives including `button()` (`ButtonStyle`), `cardContainer()`, `sectionHeader()`, `statusChip()`, `monogram()`, `themedSwitch()`, `themedCheckBox()`, `themedSeekBar()`, `inputField()`, `emptyState()`, and `QuotaBarView`.
- **Strings & Localization:** User-facing Portuguese copy belongs in `app/src/main/res/values/strings.xml`. Vector icons live in `app/src/main/res/drawable/ic_*.xml` and map via `GroupIcon` / `GroupIconResources.kt` (never use raw emojis for group identity).

### Architecture & Concurrency
- **4-Layer Separation:**
  1. `domain`: 100% pure Kotlin business logic, state machine, and quota rules. **Zero `android.*` imports allowed** so unit tests run directly on the local JVM.
  2. `data`: `SharedPreferences` persistence (`KeyValueBackend`, `FocusPreferencesStore`) with in-memory caching, atomic batch commits, and lazy daily reset by `epochDay`.
  3. `service`: `FocusAccessibilityService`, `OverlayController` (`WindowManager` attachment), overlay views (`BarrierView`, `FloatingPillView`), and `MainThreadTicker`.
  4. `ui`: Programmatic `Activity` shells, tabs (`TodayScreen`, `GroupsScreen`, `SettingsScreen`), `Dialog`-based `BottomSheet`s, and `DashboardPresenter`.
- **No Coroutines / RxJava:** Concurrency and 1 Hz countdowns use `MainThreadTicker` (`Handler(Looper.getMainLooper())`) and monotonic `SystemClock.elapsedRealtime()` via `TimeProvider`.

---

## 3. Codebase Routing Matrix

Use this table to jump directly to the 2–3 files relevant to your task without scanning the whole repository:

| Task / Responsibility | Target Directory & Key Files |
| :--- | :--- |
| **State machine, session lifecycle, pause/cooldown/quota evaluation, anti-bypass lock, foreground exit sampling** | `app/src/main/java/com/focusguard/app/domain/`<br/>• `FocusPolicyEngine.kt` (core state machine, `onForegroundLost()`)<br/>• `FocusModels.kt` (`FocusGroup`, `FocusConfig`, `InterventionAction`, `BlockReason`)<br/>• `FocusLimits.kt` (parameter bounds, presets, quota levels)<br/>• `SocialAppCatalog.kt` (curated social app definitions)<br/>• `WindowExitArbiter.kt` (verdicts from a `ForegroundSnapshot`; never reads the event package)<br/>• `SessionExitTracker.kt` (1 Hz verdict debouncer: 2 hidden ticks / 5 unknown ticks)<br/>• `TimeProvider.kt` (clock & `FocusPolicyStore` interface) |
| **Persistence, preferences, in-memory cache, lazy daily reset, process runtime** | `app/src/main/java/com/focusguard/app/data/`<br/>• `FocusPreferencesStore.kt` (storage, migrations, bitmask rollover)<br/>• `KeyValueBackend.kt` (`KeyValueBackend` & `FocusRuntime` singleton) |
| **Accessibility events, dynamic `packageNames` filter, overlay window lifecycle, floating pill & barrier UI** | `app/src/main/java/com/focusguard/app/service/`<br/>• `FocusAccessibilityService.kt` (event-driven entry only; foreground exits are tick-sampled at 1 Hz in `handleSecondTick`, never inferred from the event package)<br/>• `OverlayController.kt` (`WindowManager` attach/detach, window flags, `ensurePillAttached()`)<br/>• `BarrierView.kt` (full-screen mindful pause & strict hard-block view)<br/>• `FloatingPillView.kt` (in-session floating countdown pill)<br/>• `DebugEventLog.kt` (debug-only ring buffer, `adb logcat -s FocusGuard`)<br/>• `MainThreadTicker.kt` (1 Hz `Handler` timer) |
| **Activities, 3-tab navigation, screens, bottom sheets, app discovery** | `app/src/main/java/com/focusguard/app/ui/`<br/>• `MainActivity.kt` (3-tab shell: Hoje, Grupos, Ajustes & lock enforcement)<br/>• `OnboardingActivity.kt` (3-step first-run wizard)<br/>• `TodayScreen.kt`, `GroupsScreen.kt`, `SettingsScreen.kt`<br/>• `GroupCardView.kt`, `ReclaimedCardView.kt`, `ServiceStatusCard.kt`, `AnchorHabitCardView.kt`<br/>• `BottomSheet.kt`, `GroupConfigSheet.kt`, `AppPickerSheet.kt`, `AnchorHabitSheet.kt`, `ConfirmSheet.kt`<br/>• `InstalledAppScanner.kt`, `GroupIconResources.kt` |
| **UI state mapping & formatting** | `app/src/main/java/com/focusguard/app/ui/presenter/`<br/>• `DashboardPresenter.kt` (`TodayState`, `SettingsState`, `GroupLimitState`) |
| **Design tokens, theme helpers, native widget factories** | `app/src/main/java/com/focusguard/app/ui/design/`<br/>• `Theme.kt` (`Palette`, `TypeScale`, `Space`, `Radius`, `Motion`, `Theme`)<br/>• `UiKit.kt` (`button`, `cardContainer`, `QuotaBarView`, switches, seekbars) |
| **Resources (Portuguese copy, vector icons, accessibility config)** | `app/src/main/res/`<br/>• `values/strings.xml`, `values/themes.xml`, `values/colors.xml`<br/>• `drawable/ic_*.xml`<br/>• `xml/accessibility_service_config.xml` |
| **JVM Unit Tests (domain & data verification)** | `app/src/test/java/com/focusguard/app/`<br/>• `domain/FocusPolicyEngineTest.kt`<br/>• `domain/FocusLimitsTest.kt`<br/>• `domain/WindowExitArbiterTest.kt`<br/>• `domain/SessionExitTrackerTest.kt`<br/>• `data/FocusPreferencesStoreTest.kt` |

---

## 4. Essential Verification Commands

Run these commands from the repository root (`/home/johnb/Projects/focus-guard`):

```bash
# Host JDK 27 cannot be parsed by Kotlin 2.0.21 ("IllegalArgumentException: 27").
# Always build with the pinned JDK 21:
JAVA_HOME=$HOME/.local/share/jdks/jdk-21.0.13+9 ./gradlew testDebugUnitTest

JAVA_HOME=$HOME/.local/share/jdks/jdk-21.0.13+9 ./gradlew assembleDebug
```

- **Mandatory Gate:** Every non-trivial modification to `domain/` or `data/` **must** include or update unit tests in `app/src/test/java/com/focusguard/app/` and pass `./gradlew testDebugUnitTest` before you claim completion.
- **UI / Service Gate:** Every change to `ui/`, `service/`, or `res/` must pass `./gradlew assembleDebug` with zero compilation or resource-linking errors.

---

## 5. Agent Pitfalls & Anti-Patterns (What to Avoid)

1. **NEVER add Jetpack Compose, XML layouts, or Material/AppCompat dependencies:** Keep `app/build.gradle.kts` free of runtime library bloat. Build all UI via `ui/design/Theme.kt` and `ui/design/UiKit.kt`.
2. **NEVER weaken `FocusPolicyEngine` or anti-bypass rules:** Do not allow editing group limits, disabling monitored apps, or deleting active groups when `engine.isStrictLockActive()` returns `true` (active cooldown or exhausted daily quota).
3. **NEVER set `serviceInfo.packageNames = null` while idle:** Broad package listening is only permitted while an active pause barrier or session is running (`shouldListenBroadly`). When idle, `FocusAccessibilityService` must filter strictly by enabled target packages to maintain `0.0%` CPU usage.
4. **NEVER leak overlay views or animators:** `OverlayController` must detach views immediately when transitioning to `DismissOverlay`, and `BarrierView` must stop its breathing `ValueAnimator` when hidden or detached.
5. **NEVER use wall-clock time (`System.currentTimeMillis()`) for countdown intervals:** Always use `TimeProvider.elapsedRealtimeMillis()` (`SystemClock.elapsedRealtime()`) for session, pause, and cooldown durations so clock tampering cannot bypass blocks.
6. **NEVER ingest the entire repository for localized tasks:** `FocusPolicyEngine.kt` (~53 KB) and `FocusPreferencesStore.kt` (~25 KB) are large. Use targeted symbol searches (`rg -n`) and read narrow line slices instead of dumping whole files into context.
7. **NEVER rewrite entire 300–500 line files for small tweaks:** Use surgical search-and-replace edits to preserve existing structure, insets handling, and accessibility semantics.
8. **NEVER resurrect event-package exit detection:** Foreground exits are tick-sampled (`handleSecondTick` → `WindowExitArbiter` → `SessionExitTracker`). Do not infer an exit from `event.packageName`, do not reintroduce deferred exit confirmation timers, and never let an unfiltered IME/OEM/third-party window reach `FocusPolicyEngine`.
9. **NEVER keep the pill out of sync with the engine:** While `hasForegroundSession()` is true, call `OverlayController.ensurePillAttached()` before rendering so a pill dropped by the OEM window manager is re-attached on the next tick.

---

## 6. Agent Workflow Best Practices

1. **Consult `AGENTS.md` First:** Identify the exact layer (`domain`, `data`, `service`, `ui`, or `ui/design`) from the Routing Matrix above.
2. **Isolate 2–3 Target Files:** Use `rg -n` to locate the exact method or state property before reading file ranges (`80–120` lines). Consult `PRODUCT.md` or `ARCHITECTURE.md` only if deeper product or state-machine context is needed.
3. **Preserve Layer Purity:**
   - Keep `domain/` 100% free of Android framework imports.
   - Keep `OverlayController` strictly responsible for `WindowManager` attachment/flags, delegating visual rendering to `BarrierView` and `FloatingPillView`.
   - Route UI mutations through `MainActivity` / `FocusPreferencesStore` and refresh state snapshots via `DashboardPresenter`.
4. **Verify Before Concluding:** Run `./gradlew testDebugUnitTest` (and `./gradlew assembleDebug` when touching Android views/resources) to confirm zero regressions.
