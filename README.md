<!-- PROJECT BANNER HERO -->
<p align="center">
  <img src="https://storage.seelen.io/production/from-user/9b48fbd0-19c3-49e3-a86c-0875904a51a7/19e35c5301a.webp" alt="Permata Auto Media Player Header" width="100%" style="border-radius: 8px; max-height: 280px; object-fit: cover;">
</p>

# 📱 Permata Auto Media Player

[<img alt="Get it on Google Play" height="60" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png">](https://play.google.com/store/apps/details?id=sklchan77)

---

## 📄 Overview

**Permata Auto Media Player** is a premium, open-source audio, video, and TV suite designed with a sleek, minimalist interface. Built for seamless control, the engine focuses on playing media files organized logically in folders and playlists, rendering high-fidelity playback across multiple Android form factors.

---

## 🚀 Key Features

### 🗂️ Media Organization & Playback
* **Folder Integration:** Stream and play native media files cataloged neatly inside local folders.
* **Intelligent Memory:** Automatically preserves the last played track and unique playback timestamp position per folder.
* **Playlists & Bookmarks:** Comprehensive support for custom playlists, favorites, cues, and M3U streams.

### 📺 Advanced IPTV & Video Tuning
* **IPTV Ecosystem:** Integrated IPTV add-on with native support for XMLTV EPG schedules and Catchup protocols.
* **Modular Processing:** Powered by pluggable high-performance media engines including standard `MediaPlayer`, `ExoPlayer`, and complete `VLC`.
* **Subtitles & Tracking:** Full-scale video deployment with external subtitle tracking and multi-stream audio configuration (via VLC Engine).

### 🎛️ Acoustics & Multi-Device Optimization
* **Hardware EQ:** Dynamic audio effects including parametric Equalizers, Bass/Volume Boost, and specialized Virtualizers.
* **Granular Configurations:** Set customized playback speeds and environmental sound profiles for individual folders or tracks.
* **Automotive & TV Scaling:** Fully optimized projection support tailored explicitly for **Android Auto** and **Android TV** dashboards.

---

## 🛠️ Development & Compilation

Follow these standardized production guidelines to compile the ecosystem locally using **Android Studio** or isolated **Docker containers**.

### Prerequisites
1. Download and deploy the latest [Android SDK](https://android.com) environment.
2. Map your local system environment variables directly to your SDK directory path:

```bash
export ANDROID_SDK_ROOT=/path/to/android/sdk
```

### Build Instructions

First, clone the source tree recursively to pull down all necessary submodules:
```bash
git clone --recurse-submodules https://github.com
cd PermataAuto
```

#### Option A: Compiling Android App Bundles (.AAB)
Execute the release bundle task while specifying a unique package suffix to decouple the application layer from standard blocks:
```bash
./gradlew bundleAutoRelease -PAPP_ID_SFX=.your.custom.suffix
find \$PWD -name "*.aab"
```

#### Option B: Compiling Standalone Installers (.APK)
Generate a deployable, custom-signed package directly to your local file path:
```bash
./gradlew assembleAutoRelease -PAPP_ID_SFX=.your.custom.suffix
find \$PWD -name "*.apk"
```

#### Option C: Isolated Containerized Builds (Docker)
To maintain an immutable build workspace, compile directly within our automated Docker stack:
```bash
# Instantiate and build inside the environment
docker run -ti --name Permata sklchan77/PermataAuto

# Extract the compiled output bundles to your host machine
docker cp Permata:/home/mobiledevops/PermataAuto/permata/build/outputs/bundle/autoRelease/ .
```
*(Note: Provide your requested verification alias and key signatures when prompted by the terminal prompt).*

---

## 🤝 Acknowledgments

Special thanks to **Andrey Pavlenko** for the unwavering commitment to releasing this source code openly. This dedication keeps localized open-source development alive, secure, and accessible for everyone.

---

<p align="center">
  <sub>Licensed under the <b>GPL-3.0 License</b>. Built with precision for developers and drivers.</sub>
</p>
