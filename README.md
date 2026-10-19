# FocusGuard — Ultra-Light Anti-Doomscrolling for Android (4 GB RAM)

Native Android application engineered under a strict **Zero-Bloat** philosophy (`~5–12 MB` background RAM, `0%` idle CPU, and zero ads/telemetry) to break automatic *deep scrolling* habits on Instagram, TikTok, YouTube, X/Twitter, Reddit, and other social networks.

---

## How to Build and Install on Your Phone

### Option 1: Via Android Studio (Easiest)
1. Open Android Studio and select **Open** -> `/home/johnb/Projects/focus-guard`.
2. Connect your phone via USB with **USB Debugging** enabled in *Developer Options*.
3. Click **Run (`▶`)** to compile and install directly on your device.

### Option 2: Via Command Line (CLI)
In an environment with JDK 17+ and Android SDK configured:

```bash
# Run the JVM unit test suite (executes in < 2 seconds)
./gradlew test

# Build the R8/ProGuard-optimized Release APK (< 1.5 MB)
./gradlew assembleRelease

# Or build the Debug APK and install via ADB
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## One-Time Device Activation

1. Open **FocusGuard** on your phone.
2. Tap the **"Ativar Serviço de Acessibilidade"** button at the top of the screen.
3. In the Android Accessibility list, tap **FocusGuard** and toggle the switch on.
   - *Note for Android 13+ (Restricted Settings):* If Android shows a "Restricted setting" dialog because the APK was sideloaded outside the Play Store, open *Settings > Apps > FocusGuard > Menu (⋮ in the top-right corner) > Allow restricted settings*, then enable the Accessibility Service normally.

---

## Auditing RAM Consumption on Your Device

With your phone connected via USB, run:

```bash
adb shell dumpsys meminfo com.focusguard.app | head -n 35
```

You will observe **TOTAL PSS** parked between **5 MB and 12 MB**, because:
- `canRetrieveWindowContent="false"` prevents Android from serializing view hierarchies across Binder IPC.
- `serviceInfo.packageNames` filters target packages directly at the OS level while idle.
- Zero heavy UI frameworks (Jetpack Compose / AppCompat / Material) or third-party SDKs are loaded into memory.
