# ScrcpyDroid

<div align="center">

**Native Android-to-Android screen mirroring and remote control client**  
*Powered by the official [scrcpy](https://github.com/Genymobile/scrcpy) protocol over local Wi-Fi.*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?logo=android)](https://developer.android.com/jetpack/compose)
[![Scrcpy](https://img.shields.io/badge/Scrcpy%20Protocol-v3.1-orange.svg)](https://github.com/Genymobile/scrcpy)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

</div>

---

**ScrcpyDroid** enables one Android phone or tablet to completely control another Android device over Wi-Fi with ultra-low latency, full touch interaction, hardware buttons, real-time audio forwarding, and zero PC host requirements.

---

## ✨ Features

- 📱 **100% Android-to-Android**: No computer, no Python scripts, and no ADB server host required.
- 🚀 **Hardware-Accelerated Decoding**: Real-time H.264 & H.265 video decoding using Android `MediaCodec` rendering directly to a `SurfaceView`.
- 🔊 **Real-Time Audio Forwarding**: Streams raw 16-bit PCM stereo (48 kHz) audio directly through the controller device's speakers while muting the target phone (`audio_dup=false`).
- 📡 **mDNS Zero-Conf Discovery**: Automatically discovers nearby devices advertising Wireless Debugging (`_adb-tls-connect`, `_adb-tls-pairing`, `_adb`) via Android `NsdManager`, intelligently filtering out the local device.
- 🔐 **SPAKE2 Wireless Pairing**: Built-in Android 11+ Wireless Debugging pairing using standard 6-digit codes.
- 🎯 **Full Touch & Gesture Forwarding**: Low-latency multi-touch injection with automatic coordinate scaling for differing aspect ratios and resolutions.
- 🔙 **Native Back Gesture Integration**: System back swipe gestures on the controller device are seamlessly injected as `KEYCODE_BACK` into the controlled device.
- 🚫 **Local Status Bar Blocking**: Optional immersive mode protection (`systemGestureExclusionRects`) to prevent the controller's notification shade from dropping down during downward swipe gestures.
- 🔄 **In-App OTA Updates**: Self-updater checking releases from GitHub and prompting seamless APK installation via `FileProvider`.

---

## 🛠️ Architecture & Tech Stack

| Component | Technology |
|---|---|
| **UI Framework** | Jetpack Compose + Material 3 (BOM 2024.12.01) |
| **Language & Concurrency** | Kotlin 2.1.0 + Coroutines & StateFlow |
| **Dependency Injection** | Hilt 2.53.1 |
| **ADB Wire Protocol** | [`dadb`](https://github.com/mobile-dev-inc/dadb) (Pure Kotlin ADB) |
| **Pairing & TLS** | [`libadb-android`](https://github.com/MuntashirAkon/libadb-android) (Android 11+ SPAKE2 & TLS) |
| **Server Engine** | Official [`scrcpy-server.jar`](https://github.com/Genymobile/scrcpy) v3.1 |
| **Video Decoding** | Android `MediaCodec` (`video/avc`, `video/hevc`) |
| **Audio Playback** | Android `AudioTrack` (PCM 16-bit 48 kHz stereo) |
| **Persistence** | Jetpack DataStore Preferences |

---

## 🚀 How to Use

### 1. Prerequisites on the Target (Controlled) Device
1. Enable **Developer Options** (Tap *Build Number* 7 times in Settings > About Phone).
2. Enable **Wireless Debugging** (Android 11+).
3. Connect both devices to the same local Wi-Fi network.

### 2. First-Time Pairing (SPAKE2)
1. In the target device's **Wireless Debugging** menu, tap **"Pair device with pairing code"**.
2. Note the **6-digit pairing code** and the **port number** shown on the popup.
3. On your controller device, open **ScrcpyDroid**.
4. The target phone will appear in **Nearby Devices (mDNS)** with a green **Pair** button.
5. Tap **Pair**, enter the 6-digit code, and confirm. Once paired, the target device remembers your controller permanently.

### 3. Connect & Mirror
1. Under **Nearby Devices**, tap **Connect** next to your target device (or enter the IP and port directly).
2. ScrcpyDroid will automatically push `scrcpy-server.jar`, start the server, establish video, audio, and control streams, and begin mirroring!

---

## 🔨 Building from Source

### Prerequisites
- Android Studio Ladybug / Meerkat or later
- JDK 17
- Android SDK Platform 35

```bash
# Clone the repository
git clone https://github.com/zax4r0/ScrcpyDroid.git
cd ScrcpyDroid

# Build Debug APK
./gradlew assembleDebug

# Install directly to a connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🙏 Credits & Open Source Acknowledgements

ScrcpyDroid stands entirely on the shoulders of giants. We did not build the complex underlying protocols or engines from scratch; this app is merely a modern bridge bringing these incredible open-source tools together. 

Huge gratitude and deepest respect go out to the original creators and maintainers of these projects:

### 🌟 [scrcpy](https://github.com/Genymobile/scrcpy) by Romain Vimont ([@rom1v](https://github.com/rom1v)) & Genymobile
The foundational screen mirroring tool that changed the Android ecosystem forever.
- Provided the official `scrcpy-server.jar` (v3.1).
- Designed the ultra-efficient binary streaming protocol, touch/control message specs, and audio redirection mechanisms.

### 🌟 [dadb](https://github.com/mobile-dev-inc/dadb) by [mobile.dev](https://github.com/mobile-dev-inc)
A pure Kotlin ADB implementation for the JVM.
- Enables direct ADB communication over TCP/IP sockets without needing native CLI binaries or root access.
- Handles RSA keypair generation, authentication handshakes, and stream multiplexing cleanly on Android.

### 🌟 [libadb-android](https://github.com/MuntashirAkon/libadb-android) by Muntashir Al-Islam ([@MuntashirAkon](https://github.com/MuntashirAkon))
A modern Java/Kotlin library for ADB on Android.
- Provides the SPAKE2 authentication protocol implementation and TLS handshake logic required for Android 11+ Wireless Debugging pairing.

### 🌟 [ScrcpyForAndroid](https://github.com/zwc456baby/ScrcpyForAndroid) by [@zwc456baby](https://github.com/zwc456baby)
An early Android client implementation of the scrcpy protocol.
- Provided valuable insights into running scrcpy client logic directly on Android devices.

### 🌟 [Okio](https://github.com/square/okio) by [Square](https://github.com/square)
A modern I/O library for Kotlin and Java.
- Powers the low-level binary streaming, buffer parsing, and control message encoding throughout the client.

### 🌟 [Bouncy Castle](https://github.com/bcgit/bc-java) by Legion of the Bouncy Castle
- Provides the cryptographic algorithms used for generating self-signed X.509 certificates during ADB TLS handshakes.

---

## 📄 License

```text
Copyright 2026 zax4r0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
