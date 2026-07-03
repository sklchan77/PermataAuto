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



# === New entries for the KeyEventHandler.java for web page scrolling ===

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



# === New entries for the MediaSessionCallBack.java for web page scrolling ===

# Prevent ProGuard from altering the browser reflection entrypoints
-keep class my.app.permata.ui.activity.MainActivity {
    public static my.app.permata.ui.activity.MainActivity getActiveInstance();
}

-keep class my.app.permata.ui.activity.MainActivityDelegate {
    public static my.app.permata.ui.activity.MainActivityDelegate get(android.content.Context);
    public *** getActiveMainActivityFragment();
    public *** getActiveFragment();
}

-keep class my.app.permata.addon.web.WebBrowserFragment {
    public android.webkit.WebView getWebView();
}


# Prevent ProGuard optimization from stripping background context-to-delegate mapping rules
-keep class my.app.utils.ui.activity.ActivityDelegate {
    public static *** getContextToDelegate();
}

-keep class my.app.permata.auto.EventDispatcher { *; }

