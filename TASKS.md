# Task Progress & Execution Matrix — FocusGuard

## Milestone 1: Bootstrap & Pure Domain (Core Engine & Contracts)
- [x] Task 1.1: [Docs] Define `PRODUCT.md`, `ARCHITECTURE.md`, and `TASKS.md` (Triad Source of Truth)
- [x] Task 1.2: [Scaffold] Configure lean Gradle Kotlin DSL structure (Zero external runtime libraries, JUnit 5 for local JVM tests)
- [x] Task 1.3: [Contract] Implement domain models (`SocialAppCatalog`, `FocusConfig`, `InterventionAction`, `TimeProvider`) in `com.focusguard.app.domain`
- [x] Task 1.4: [Test] Write unit test suite for `FocusPolicyEngine` (Mindful pause, session countdown, pause on app exit, cooldown activation, daily quota exhaustion, lazy day rollover, and anti-bypass lock)
- [x] Task 1.5: [Impl] Implement `FocusPolicyEngine` passing 100% of unit tests

## Milestone 2: Ultra-Light Persistence (`SharedPreferences`)
- [x] Task 2.1: [Contract & Impl] Implement `FocusPreferencesStore` with lightweight package-set serialization, daily accumulated usage, cooldown timestamps, and lazy reset by `epochDay`
- [x] Task 2.2: [Test] Validate persistence and automatic day rollover in domain unit tests

## Milestone 3: Accessibility Engine & Native Overlays (`TYPE_ACCESSIBILITY_OVERLAY`)
- [x] Task 3.1: [Impl] Implement `MainThreadTicker` using `Handler(Looper.getMainLooper())` with zero coroutine allocations
- [x] Task 3.2: [Impl] Implement `OverlayController` with programmatic native Android Views:
  - Mindful Pause Screen (countdown + immediate exit button + session unlock buttons)
  - Floating Session Pill (compact top-corner timer during unlocked sessions)
  - Strict Block Screen (10-min Cooldown or Exhausted Daily Quota)
- [x] Task 3.3: [Impl] Implement `FocusAccessibilityService` integrating `FocusPolicyEngine`, `OverlayController`, dynamic `serviceInfo.packageNames` filtering, and Launcher exit detection

## Milestone 4: Configuration Screen & Anti-Bypass Lock
- [x] Task 4.1: [Impl] Implement `MainActivity` (inheriting directly from `android.app.Activity`) with:
  - Accessibility Service status banner + shortcut to `Settings.ACTION_ACCESSIBILITY_SETTINGS`
  - Toggle switches for `SocialAppCatalog` apps
  - Parameter selectors (Pause duration, session duration, cooldown, and daily quota)
  - Active lock banner and Read-Only Mode enforcement when anti-bypass lock is active
- [x] Task 4.2: [Config] Configure `AndroidManifest.xml` and `res/xml/accessibility_service_config.xml` (zero internet permission, R8 enabled for release builds)

## Milestone 5: Quality Verification (`pre-flight`) & APK Build Guide
- [x] Task 5.1: [Gate] Run JVM unit tests and build/lint verification
- [x] Task 5.2: [Docs] Add quick installation instructions via `adb install` and Android Studio

## Milestone 6: Positive Reinforcement & Mindful Friction Package
- [x] Task 6.1: [Contract] Expand `FocusConfig`, `AppUsageState`, `FocusRewardState`, `FocusRewardSummary`, and `InterventionAction`
- [x] Task 6.2: [Engine] Implement Progressive Pause (+5s/session), 1 Hz Breathing Guide, 1-min Quick Micro-Session, Voluntary Early-Exit Bonus, and Bitmask Consistency
- [x] Task 6.3: [Data] Persist positive reinforcement metrics and daily consistency bitmask rollover in `FocusPreferencesStore`
- [x] Task 6.4: [Overlay & UI] Add haptic celebration on impulse resistance, identity goal anchor, interactive floating pill (corner swap + early exit), and Achievements Card in `MainActivity`
- [x] Task 6.5: [Test] Cover positive reinforcement mechanics in the JUnit 5 test suite

## Milestone 7: v1.2.0 — Extreme Optimization, Polish & Bug Fixes
- [x] Task 7.1: [Bugfix] Pause active session immediately when screen turns off (`PowerManager.isInteractive` + `onScreenTurnedOff`)
- [x] Task 7.2: [Bugfix] Expand transient package filter (`SwiftKey`, `Honeyboard`, `PermissionController`) and credit voluntary early exit when a paused session expires in background
- [x] Task 7.3: [Perf] Implement `BatchEditor` (`editBatch`), in-memory caching for `AppUsageState`/`FocusRewardState`, and process-wide `FocusRuntime` singleton
- [x] Task 7.4: [Perf] Eliminate per-tick `GradientDrawable` and `Color.parseColor` allocations in `OverlayController`
- [x] Task 7.5: [Build] Migrate `compilerOptions` to Kotlin 2.0 DSL and enable R8-minified Release builds (`assembleRelease`)

## Milestone 8: v1.2.2 — Samsung Keyboard (One UI) & Auxiliary Service Support
- [x] Task 8.1: [Bugfix] Prevent floating session pill from pausing or disappearing when opening default Samsung Keyboard and Smart Suggestions (`com.samsung.android.smartsuggestions`, `scs`, `icecone`, `neatart`, `sdk.handwriting`, `stickercenter`, `aremoji`, `intelligence`, `aicore`)
- [x] Task 8.2: [A11y] Intercept transient system packages and window classes directly in `FocusAccessibilityService.onAccessibilityEvent` and prepopulate Samsung packages in `refreshImePackageCache`
- [x] Task 8.3: [Test] Validate Samsung Keyboard and auxiliary services in JVM unit test suite

## Milestone 9: v1.2.3 — Universal Transient & IME Isolation Architecture
- [x] Task 9.1: [A11y] Enable `canRetrieveWindowContent="true"` in `accessibility_service_config.xml` to allow `getWindows()` detection of active IME windows
- [x] Task 9.2: [A11y] Add `FLAG_ALT_FOCUSABLE_IM` to floating pill window parameters to prevent IME window manager conflicts
- [x] Task 9.3: [Architecture] Implement intent-based launcher & launchable user app discrimination (`isTransientPackageOrWindow`), guaranteeing zero false session exits from non-launchable OEM/system/IME services
- [x] Task 9.4: [Test] Verify suite with Gradle unit tests and assemble release APK

## Milestone 10: v1.3.0 — UI/UX & Mindful Friction Evolution
- [x] Task 10.1: [Contract] Add `brandColorHex` to `SocialAppCatalog.kt` and `FocusPreset` enum with preset configurations in `FocusModels.kt`
- [x] Task 10.2: [Test] Write unit tests for `FocusPreset` validation and catalog brand color coverage
- [x] Task 10.3: [Impl] Upgrade `OverlayController` with visual breathing ring animator, horizontal quota gauge, dynamic urgency warnings for the floating pill, and ripple effects
- [x] Task 10.4: [Impl] Transform `MainActivity` with Bento-style 2x2 dashboard, 7-day pill calendar, one-tap focus presets, brand accents, and 4dp quota progress bars on app rows
- [x] Task 10.5: [Gate] Verify JVM unit tests, compile release build, and confirm Zero-Bloat invariants

## Milestone 11: v1.4.0 — Focus Limit Groups, Shared Quotas & Dynamic App Picker
- [x] Task 11.1: [Docs] Update `PRODUCT.md`, `ARCHITECTURE.md`, and `TASKS.md` for Focus Groups, Shared Quotas, and Dynamic `<queries>` App Picker
- [x] Task 11.2: [Domain] Introduce `FocusGroup`, expand `FocusConfig` & `InterventionAction`, dynamic catalog lookup in `SocialAppCatalog`, and group-aware policy & lock engine in `FocusPolicyEngine`
- [x] Task 11.3: [Data] Compact delimiter-based group & custom app serialization and automatic migration in `FocusPreferencesStore`
- [x] Task 11.4: [Test] Comprehensive JUnit 5 unit tests for shared group quotas, cooldown isolation, dynamic custom apps, and preferences store migration
- [x] Task 11.5: [Service & A11y] Add `<queries>` launcher intent filter in `AndroidManifest.xml`, dynamic multi-group package filtering in `FocusAccessibilityService`, and group indicator in `OverlayController`
- [x] Task 11.6: [UI] Implement `InstalledAppScanner`, native dark-themed `AppPickerDialog`, `GroupConfigDialog`, and group-centric dashboard cards in `MainActivity`
- [x] Task 11.7: [Gate] Validate entire JVM test suite and compile release APK (`./gradlew test assembleRelease`)

## Milestone 12: v1.5.0 — Flat & Accessible Dark UI/UX Overhaul
- [x] Task 12.1: [Phase 0 - Domain & Specs] Sync Triad docs (`PRODUCT.md`, `ARCHITECTURE.md`, `TASKS.md`), bump version to 1.5.0, audit anchor habit persistence, implement `FocusLimits.kt` + tests, and produce approved visual mockups
- [x] Task 12.2: [Phase 1 - UI Foundation] Implement `FocusTheme.kt`, `themes.xml`, `strings.xml`, `BottomSheet.kt`, and split `MainActivity.kt` foundation
- [x] Task 12.3: [Phase 2 - Main Screen] Implement `SummaryCardView.kt`, `GroupCardView.kt`, and `AnchorHabitSheet.kt` in `MainActivity.kt`
- [x] Task 12.4: [Phase 3a - Group & Picker Sheets] Implement `GroupConfigSheet.kt` and `AppPickerSheet.kt`, replacing legacy dialogs
- [x] Task 12.5: [Phase 3b - Overlay Redesign] Overhaul `OverlayController.kt` (flat mindful pause, thumb-zone CTAs, clean hard block, borderless pill, edge-to-edge insets)
- [x] Task 12.6: [Gate - Verification & Release] Run JVM unit tests, audit APK size (< 1.5 MB), check memory footprint (< 12 MB RAM), and assemble release build

## Milestone 13: v2.0.0 — Warm Dusk Redesign, Three-Tab Shell & Onboarding
- [x] Task 13.1: [Docs & Domain] Approve the Warm Dusk direction, sync the Triad docs, add `GroupIcon` (stable keys + legacy emoji migration), `ActiveSessionSnapshot`, 7-day reward lists, and `totalSessionSeconds` on the floating timer
- [x] Task 13.2: [Design System] Implement `ui/design/Theme.kt` (palette, type scale, spacing, radii, motion, insets, haptics, reduced motion) and `ui/design/UiKit.kt` (buttons, cards, chips, switches, seekbars, inputs, empty states, `QuotaBarView`)
- [x] Task 13.3: [Resources] Rewrite `colors.xml`, `themes.xml`, `strings.xml` (full pt-BR copy + plurals) and add 19 hand-written vector drawables including 11 `GroupIcon` glyphs
- [x] Task 13.4: [Presentation] Implement `DashboardPresenter` (`TodayState`, `SettingsState`, `GroupLimitState`, `AppUsageRow`, formatting and day labels)
- [x] Task 13.5: [Screens] Implement `TodayScreen`, `GroupsScreen`, and `SettingsScreen`; rebuild `MainActivity` as a 3-tab shell with custom bottom navigation, onboarding redirect, and anti-bypass lock enforcement in every entry point
- [x] Task 13.6: [Sheets & Cards] Rewrite `BottomSheet`, add `ConfirmSheet`, `ServiceStatusCard`, `ReclaimedCardView`, `AnchorHabitCardView`; rewrite `GroupCardView`, `GroupConfigSheet`, `AppPickerSheet`, and `AnchorHabitSheet`
- [x] Task 13.7: [Onboarding] Implement `OnboardingActivity` (activate service, choose apps, pick a rhythm), persist `onboarding_completed_v2`, and auto-skip for existing installations
- [x] Task 13.8: [Overlay] Split the overlay into `BarrierView` + `FloatingPillView`; rewrite `OverlayController` for window lifecycle, keep-screen-on, reduced motion, and settings barrier preview
- [x] Task 13.9: [Data] Add onboarding flag, `resetTodayStats()`, `resetAllStats()`, and group icon migration to `FocusPreferencesStore`
- [x] Task 13.10: [Gate] Bump version to 2.0.0 (versionCode 12), update docs, run the JVM suite, and verify the release APK stays far below the 1.5 MB budget

## Milestone 14: v2.0.1 — Keyboard-Proof Session Timer & Strict Block Polish
- [x] Task 14.1: [Overlay] Redesign the strict hard block around a live `mm:ss` countdown (or `Amanhã` when the quota is exhausted), a single muted group/reason line, and one exit action
- [x] Task 14.2: [Domain] Add `WindowExitArbiter` (pure, JVM-testable) and `FocusPolicyEngine.hasForegroundSession()` to separate a real app switch from IME/OEM auxiliary windows
- [x] Task 14.3: [Service] Confirm candidate exits against interactive `TYPE_APPLICATION` windows (300 ms + one 400 ms re-check), keep the launcher on the fast path, and stop overwriting `currentForegroundPackage` with transient packages
- [x] Task 14.4: [Overlay] Self-heal the floating pill when `WindowManager` detaches it and make the pill window immune to IME insets (`SOFT_INPUT_ADJUST_NOTHING`, zero insets fitting)
- [x] Task 14.5: [Tests & Docs] Cover the arbiter with 9 JVM tests plus the `hasForegroundSession` lifecycle test; sync `ARCHITECTURE.md`, `AGENTS.md`, and `PRODUCT.md`
- [x] Task 14.6: [Gate] Bump version to 2.0.1 (versionCode 13), run the JVM suite, assemble the release build, and publish the GitHub release
