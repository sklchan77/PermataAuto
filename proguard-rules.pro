-keepattributes LineNumberTable,SourceFile
-keepnames class my.app.** { *; }
-keep class my.app.permata.auto.** { *; }
-keep class org.videolan.libvlc.** { *; }
-keep class my.app.permata.vfs.sftp.** { *; }
-keep class my.app.permata.vfs.smb.** { *; }
-keep class my.app.permata.vfs.gdrive.** { *; }
-keep class androidx.car.app.** { *; }
-keep class org.chromium.net.impl.NativeCronetEngineBuilderImpl { *; }

-dontwarn com.sun.jna.platform.win32.**
-dontwarn com.jcraft.jsch.PageantConnector
-dontwarn okio.*

# PROGUARD RULES FOR YOUR CUSTOM PACKAGE ROOT & REFLECTION ACCESSORS
-keepnames class my.app.** { *; }
-keep class my.app.permata.** { *; }

# CRITICAL FIX: Explicitly lock the exact fields queried by ExoPlayerEngine's reflection engine hooks
-keep class androidx.media3.exoplayer.ExoPlayerImpl {
    androidx.media3.exoplayer.ExoPlayerImplInternal internalPlayer;
}
-keep class androidx.media3.exoplayer.ExoPlayerImplInternal {
    androidx.media3.common.util.HandlerWrapper handler;
}

# SUB-SYSTEM: STEERING WHEEL MEDIA SCROLL INTERCEPTION ENGINE
# 1. Prevent optimization stripping on core background event handlers
-keep class my.app.permata.action.KeyEventHandler {
    public static boolean handleKeyEvent(...);
    private static *** scanFragmentsForWebView(...);
}

# 2. Preserve runtime UI instance bridge accessor mapping
-keep class my.app.permata.ui.activity.MainActivity {
    public static *** getActiveInstance();
}

# 3. Prevent structural obfuscation of all custom WebView components globally
# This guarantees targetWebView.getClass().getName() evaluates accurately for YouTube security guards
-keep class * extends android.webkit.WebView
