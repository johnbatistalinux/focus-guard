# Keep AccessibilityService and MainActivity entry points intact
-keep class com.focusguard.app.service.FocusAccessibilityService { *; }
-keep class com.focusguard.app.ui.MainActivity { *; }
-optimizations !code/allocation/variable
