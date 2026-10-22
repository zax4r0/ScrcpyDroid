# ScrcpyDroid

Modern, native Android-to-Android screen mirroring and remote control client powered by the official [scrcpy](https://github.com/Genymobile/scrcpy) v3.1 protocol over Wi-Fi.

No PC host required. Control an Android phone from another Android phone over local Wi-Fi.

## Features

- **Pure Kotlin & Jetpack Compose**: Built with modern Material 3, Hilt dependency injection, and Kotlin Coroutines/Flow.
- **Hardware-Accelerated Video Decoding**: Low-latency H.264/H.265 decoding using Android's native `MediaCodec` rendering directly to `SurfaceView`.
- **Pure Kotlin ADB Engine**: Uses `dadb` for pure JVM/Kotlin ADB communication without invoking native binaries (no SELinux permission issues on Android 10+).
- **SPAKE2 Wireless Pairing**: Built-in Android 11+ Wireless Debugging pairing using standard 6-digit code.
- **mDNS Device Discovery**: Automatic zero-conf discovery (`_adb-tls-connect`, `_adb-tls-pairing`, `_adb`) using Android `NsdManager` with local device filtering.
- **Full Touch & Hardware Key Controls**: Touch coordinate scaling, tap, swipe, and remote hardware keys (Back, Home, App Switch, Power, Rotate).
- **Native Back Gesture Forwarding**: System back swipes are forwarded directly to the controlled device.
- **In-App OTA Updates**: Direct GitHub Release checks and self-update installation.

## Tech Stack

- **Android Gradle Plugin**: 8.7.3
- **Kotlin**: 2.1.0
- **Jetpack Compose**: BOM 2024.12.01 with Material 3
- **Dependency Injection**: Hilt 2.53.1
- **ADB & Wireless Debugging**: `dadb` 1.2.7 + `libadb-android` 3.1.1
- **Scrcpy Server**: Official `scrcpy-server.jar` v3.1

## License

Apache License 2.0
