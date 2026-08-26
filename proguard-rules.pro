# ==============================================================================
# 1. CORE ATTRIBUTES & GENERAL RULES
# ==============================================================================
-keepattributes LineNumberTable,SourceFile,Signature,InnerClasses,EnclosingMethod,Annotation

# ==============================================================================
# 2. DYNAMIC MODULES, ADDONS & MEDIA ENGINES (AAB / PLAY FEATURE DELIVERY)
# ==============================================================================
# CRITICAL: Prevent R8 from stripping SPI Service Provider descriptors for ExoPlayer, VLC, etc.
-keepresources META-INF/services/**
-keep class * implements my.app.permata.media.engine.MediaEngineProvider { *; }

# Protect all Addons, Dynamic Feature Modules, and Media Engines
-keep class my.app.permata.addon.** { *; }
-keep class my.app.permata.auto.** { *; }
-keep class my.app.permata.engine.** { *; }
-keep class my.app.permata.mlkit.** { *; }
-keep class my.app.permata.opusmt.** { *; }
-keep class my.app.permata.whisper.** { *; }

# Preserve Reflection calls on all ActivityFragment subclasses (e.g., getWebView())
-keepclassmembers class * extends my.app.utils.ui.fragment.ActivityFragment {
    public *** getWebView(...);
}

# ==============================================================================
# 3. NATIVE C++ JNI BINDINGS (WHISPER, VLC, POI, CRONET)
# ==============================================================================
# Preserve all native C/C++ method signatures across the entire app
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class org.videolan.libvlc.** { *; }
-keep class org.chromium.net.impl.NativeCronetEngineBuilderImpl { *; }

# ==============================================================================
# 4. VIRTUAL FILE SYSTEMS (VFS) & AUTOMOTIVE PERMISSIONS
# ==============================================================================
-keep class my.app.permata.vfs.sftp.** { *; }
-keep class my.app.permata.vfs.smb.** { *; }
-keep class my.app.permata.vfs.gdrive.** { *; }
-keep class androidx.car.app.** { *; }
-keep class my.app.permata.auto.EventDispatcher { *; }

# ==============================================================================
# 5. CUSTOM PACKAGE ROOT & REFLECTION ACCESSORS
# ==============================================================================
-keepnames class my.app.** { *; }
-keep class my.app.permata.** { *; }

# ==============================================================================
# 6. EXOPLAYER INTERNAL REFLECTION ENGINE HOOKS
# ==============================================================================
-keep class androidx.media3.exoplayer.ExoPlayerImpl {
    androidx.media3.exoplayer.ExoPlayerImplInternal internalPlayer;
}
-keep class androidx.media3.exoplayer.ExoPlayerImplInternal {
    androidx.media3.common.util.HandlerWrapper handler;
}

# ==============================================================================
# 7. STEERING WHEEL MEDIA SCROLL & WEBVIEW INTERCEPTION ENGINE
# ==============================================================================
# 1. Prevent optimization stripping on core background event handlers
-keep class my.app.permata.action.KeyEventHandler {
    public static boolean handleKeyEvent(...);
    private static *** scanFragmentsForWebView(...);
}

# 2. Preserve runtime UI instance bridge accessor mapping
-keep class my.app.permata.ui.activity.MainActivity {
    public static *** getActiveInstance();
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

# Prevent structural obfuscation of all custom WebView components globally
-keep class * extends android.webkit.WebView

# ==============================================================================
# 8. THIRD-PARTY LIBRARY DONTWARN RULES
# ==============================================================================
-dontwarn com.sun.jna.platform.win32.**
-dontwarn com.jcraft.jsch.PageantConnector
-dontwarn okio.**