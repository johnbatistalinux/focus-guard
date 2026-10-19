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

### Journey 1: Lean Onboarding & Configuration
1. User opens **FocusGuard**.
2. Views the Accessibility Service status and a direct shortcut button to enable it in Android Settings.
3. From the curated social media catalog (Instagram, TikTok, YouTube, X/Twitter, Reddit, Facebook, Threads, Kwai), toggles the switches for the apps to monitor.
4. Adjusts (or keeps recommended defaults for) the global parameters:
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
1. If the user attempts to reopen Instagram during the 10-minute Cooldown (or after exhausting the 30-minute daily quota), the overlay presents the **Strict Block** screen:
   - Displays the remaining Cooldown timer (or informs that today's quota is exhausted until midnight).
   - Only available button: **"Return to Home Screen"**.

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
6. **Curated Fixed Catalog:** Avoids `QUERY_ALL_PACKAGES` permission by targeting official social media packages:
   - Instagram (`com.instagram.android`)
   - TikTok (`com.zhiliaoapp.musically` / `com.ss.android.ugc.trill`)
   - YouTube (`com.google.android.youtube`)
   - X / Twitter (`com.twitter.android`)
   - Reddit (`com.reddit.frontpage`)
   - Threads (`com.instagram.barcelona`)
   - Facebook (`com.facebook.katana`)
   - Kwai (`com.kwai.video`)
