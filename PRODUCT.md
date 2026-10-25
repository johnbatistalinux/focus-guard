# Product Specification — FocusGuard (Ultra-Light Anti-Doomscrolling)

## 1. Problem & Value Proposition

### The Problem
Commercial social media blockers and screen-time management apps (StayFree, AppBlock, One Sec) suffer from two critical flaws on entry-level and mid-range devices (such as smartphones with **4 GB of RAM**):
1. **Bloatware & Excessive RAM Footprint (150 MB – 300 MB):** They bundle dozens of ad SDKs (AdMob, UnityAds), telemetry trackers, heavy databases, and hybrid frameworks that choke the system and trigger Android's *Low Memory Killer* (LMK).
2. **Inefficient Polling & Battery Drain:** Poorly architected background services run continuous polling loops instead of reacting to OS-level window events.

### Value Proposition
**FocusGuard** is a native Android social media interceptor engineered under a strict **Zero-Bloat** philosophy:
- **Strict Memory Budget:** Background footprint between **5 MB and 12 MB of RAM**, using 100% native platform components preloaded in Android's `Zygote` process (zero Jetpack Compose, zero third-party runtime libraries).
- **Zero Idle CPU (0% Idle CPU):** 100% kernel/framework event-driven architecture via `AccessibilityService` with dynamic package filtering (`packageNames`).
- **Total Privacy (Zero-Network):** Does not even declare `android.permission.INTERNET` in the manifest. Zero ads, zero telemetry.

### Success Metrics (Technical SLAs)
| Metric | Strict Target |
| :--- | :--- |
| **Background RAM Footprint** | `< 15 MB` (Target: `5–12 MB` in `dumpsys meminfo`) |
| **Idle CPU Usage** | `0.0%` when no monitored app is transitioning |
| **Interception Latency** | `< 50 ms` (zero feed flash before visual barrier) |
| **Final Release APK Size** | `< 1.5 MB` |
| **Required Permissions** | Only `BIND_ACCESSIBILITY_SERVICE` (uses native `TYPE_ACCESSIBILITY_OVERLAY`) |

---

## 2. Target Persona & User Journeys

### Primary Persona
Android smartphone user with 4 GB of RAM who wants to break the automatic *deep scrolling* habit on Instagram and other social networks without sacrificing OS responsiveness or being bombarded by ads.

### Journey 1: Three-Step Onboarding & Configuration
1. User opens **FocusGuard** for the first time and enters a three-step wizard (existing installations skip it automatically):
   - **Step 1 — Activate protection:** explains why the accessibility service is required and links directly to `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
   - **Step 2 — Choose what to monitor:** curated catalog of social networks (Instagram, TikTok, YouTube, X/Twitter, Reddit, Facebook, Threads, Kwai) with multi-select rows.
   - **Step 3 — Pick a starting rhythm:** one of the three presets (*Equilíbrio*, *Foco Total*, *Desmame Leve*), which creates the first group.
2. The app then opens the three-tab shell: **Hoje** (daily briefing), **Grupos** (limits and apps) and **Ajustes** (service, anchor habit, data, about).
3. From **Grupos**, the user can create additional categories and adjust (or keep recommended defaults for) the group parameters:
   - **Mindful Pause Duration** (Default: `10 seconds`)
   - **Allowed Session Duration** (Default: `5 minutes`)
   - **Post-Session Cooldown** (Default: `10 minutes`)
   - **Maximum Daily Quota** (Default: `30 minutes`)

### Journey 2: Impulsive App Launch (Mindful Friction)
1. User taps the **Instagram** icon.
2. Instantly (`< 50 ms`), before any feed content is visible, `FocusAccessibilityService` attaches the full-screen overlay (`TYPE_ACCESSIBILITY_OVERLAY`).
3. The minimalist dark screen displays:
   - **Mindful Pause** countdown (`10s... 9s...`) with a 1 Hz breathing guide.
   - Remaining daily quota for today (e.g., `20 min remaining today`).
   - Highlighted primary button: **"Close & Return to What Matters"** (triggers `GLOBAL_ACTION_HOME` immediately with haptic confirmation).
   - Secondary options (enabled only after countdown reaches 0s): **"Quick Reply (1 min)"** or **"Unlock 5 min Session"**.

### Journey 3: Mindful Usage with Floating Session Pill
1. If the user waits out the pause and confirms entry, the full-screen barrier is detached and replaced by a **minimalist floating pill** at the top corner of the screen (`04:59`).
2. If the user exits Instagram before the session expires (e.g., returns to the Home screen or switches to WhatsApp), the timer pauses immediately and the pill detaches.
3. When the session timer reaches `00:00`:
   - FocusGuard executes `performGlobalAction(GLOBAL_ACTION_HOME)`, ejecting the user from the feed.
   - Starts the **10-minute Post-Session Cooldown** for that app.

### Journey 4: Strict Block on Cooldown or Exhausted Daily Quota
1. If the user attempts to reopen Instagram during the 10-minute Cooldown (or after exhausting the 30-minute daily quota), the overlay presents the **Strict Block** screen in a strict, minimalist layout:
   - A live `mm:ss` countdown for the Cooldown, or **"Amanhã"** when the daily quota is exhausted.
   - One short muted line with the group and reason (e.g. *"Grupo Redes sociais · libera às 14:32"*).
   - Only available button: **"Voltar ao início"**.

---

## 3. Business Rules & Non-Negotiable Invariants

1. **OS-Level Package Filtering:** The accessibility service must **never** listen to all packages on the device (`packageNames = null` is prohibited) while idle. Whenever the user toggles a monitored app, `AccessibilityServiceInfo.packageNames` must be dynamically updated to only the active target packages.
2. **Immediate View Deallocation:** When no barrier or active session is on screen, zero overlay `View` instances may remain attached to `WindowManager`.
3. **Anti-Bypass Lock:**
   - While any monitored app is in **Cooldown** or has its **Daily Quota Exhausted**, the FocusGuard configuration screen enters **Read-Only Mode (Active Lock)**: the user cannot disable app switches or increase daily limits until the cooldown expires or the day rolls over.
4. **Lazy Daily Reset:** Do not use background `AlarmManager` jobs at midnight to reset counters. Daily resets must be evaluated lazily by comparing the current `epochDay` (`LocalDate`) upon user/service interaction.
5. **Positive Reinforcement & Mindful Friction (Zero-Bloat):**
   - **Resisted Impulses & Reclaimed Time Scoreboard:** Tracks conscious exits at the barrier and converts saved time into tangible real-life equivalents (pages read, walks taken).
   - **Identity Anchor & Haptic Celebration:** The green exit button displays a personal goal chosen by the user and triggers `HapticFeedbackConstants.CONFIRM` upon resisting an impulse.
   - **Quick Micro-Session (1 min) & Voluntary Early-Exit Bonus:** Supports 1-minute sessions for quick replies and reduces Cooldown by 50% (or waives it completely if `< 60s` used) when the user voluntarily exits early.
   - **Resilient Bitmask Consistency (`Long`):** Tracks recent days within the daily goal using a single 64-bit integer (8 bytes), without wiping historical progress over a single slip.
6. **Hybrid Catalog & Zero-Bloat App Discovery (v1.4):**
   - Combines a curated, zero-query catalog of popular social networks (Instagram, TikTok, YouTube, X/Twitter, Reddit, Threads, Facebook, Kwai) with **Dynamic App Selection**.
   - Instead of the restricted and privacy-invasive `QUERY_ALL_PACKAGES` permission, declares `<queries>` with `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`, allowing users to add any installed game, shopping app, or browser to custom focus groups while strictly preserving zero-bloat and Google Play policy compliance.
7. **Tactile & Visual Mindfulness System (v1.3):**
   - **Visual Breathing Ring:** Smooth 4s inhalation/exhalation cycle using zero-allocation `ValueAnimator`.
   - **Bento Mastery Dashboard:** 2x2 grid highlighting mastery percentage, reclaimed time, and 7-day consistency pills.
   - **One-Tap Focus Presets:** Preconfigured profiles (*Equilíbrio*, *Foco Total*, *Desmame Leve*) with anti-bypass lock enforcement.
   - **App Brand Accents & Native Progress Bars:** Visual brand identity pills and 4dp quota gauges per app.
8. **Focus Limit Groups & Shared Quotas (v1.4):**
   - **Category-Based Grouping (`FocusGroup`):** Users organize apps into focused categories (e.g., *Redes Sociais*, *Jogos*, *Vídeos & Streaming*), each with independent timers (mindful pause, session duration, cooldown, daily quota).
   - **Shared Quota Pool:** Apps in the same group share a unified daily quota. Spending time in any app in the group deducts from the group balance. Exhausting the group quota blocks all apps in that category while leaving others unaffected.
   - **Shared Group Cooldown:** Exiting a session in one app places the entire group in cooldown, preventing impulse app-hopping (e.g. from Instagram directly into TikTok).
9. **Warm Dusk Design System & Three-Tab Shell (v2.0):**
   - **Warm Dusk Palette:** Warm dark canvas `#12100E`, surface `#1C1815`, elevated `#27211C`, single amber accent `#E9B872`, sage success `#9CC5A1`, clay danger `#D9635A`. Calm, low-glare, and fully native.
   - **Design Tokens (`ui/design/Theme.kt` + `UiKit.kt`):** Palette, type scale, 8pt spacing grid, radii, motion durations, haptics, reduced-motion detection, and reusable primitives (buttons, cards, chips, switches, seekbars, quota bars, empty states).
   - **Three Tabs — Hoje, Grupos, Ajustes:** A custom bottom navigation bar (no navigation library) with a daily briefing, per-group limit management, and service/data settings.
   - **Onboarding Wizard:** Separate `OnboardingActivity` with three steps (activate service, choose apps, pick a rhythm), shown only on first run and auto-skipped for existing users.
   - **Stable Vector Iconography:** `GroupIcon` enum with 11 curated vector icons replacing emoji, with transparent migration from legacy emoji values.
   - **Readable Numbers Over Metaphors:** Quotas are always shown as `used of total` with semantic colors and a compact 7-day consistency strip.
   - **Testable Barrier Preview:** Ajustes can render the real overlay in preview mode without touching policy state.
   - **Accessibility & Ergonomics (WCAG AA):** Minimum 48dp touch targets, font scaling resilience, edge-to-edge window insets, content descriptions on icon-only controls, and centralized Portuguese copy in `strings.xml`.
   - **Unified Domain Limits (`FocusLimits`):** Parameter bounds, session-to-quota coercion, quota level thresholds, and preset matching reside strictly in the pure Kotlin domain layer.


