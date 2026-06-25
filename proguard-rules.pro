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
