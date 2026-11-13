# OpenEmux Mobile: R8 rules for the release build (issue 916).
#
# The engine (libretroarch-activity.so) reaches the Java side by name through
# JNI: the RetroArch glue in com.retroarch (RetroActivityCommon and friends)
# and the OpenEmux hooks on GameActivity. None of those names may change or go.

# The RetroArch Java glue, called from C by name (getVolumeCount, doVibrate,
# getPersistedSafTrees, onRetroArchExit...) and from the generated manifest.
-keep class com.retroarch.** { *; }

# The OpenEmux hooks the engine calls on the activity (onExternalMenuRequested,
# onExternalMenuInput, onOverlayChanged, onToggleStates, onMuteChanged,
# onAnchorFeedback, onPadShortcut, autoSaveOnPause, onResumedIntoPause).
-keepclassmembers class * extends com.retroarch.browser.retroactivity.RetroActivityCommon {
    public *;
}

# The SAF file system of libretro-common, called from C.
-keep class com.libretro.** { *; }
-keep class org.libretro.** { *; }

# Native methods keep their names (the default rules do this; stated here so a
# change of the default file never drops them).
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
