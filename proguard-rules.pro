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

-keepnames class androidx.media3.exoplayer.ExoPlayerImpl { *; }
-keepnames class androidx.media3.exoplayer.ExoPlayerImplInternal { *; }

# PROGUARD ADDITIONS FOR YOUR CUSTOM PACKAGE ROOT
-keepnames class my.app.** { *; }
-keep class my.app.permata.** { *; }