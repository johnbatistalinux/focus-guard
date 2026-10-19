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
