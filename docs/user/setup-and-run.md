# Setup & Run Guide — Chege Photos Android

This guide explains how to build, install, and run the Chege Photos Android application on a physical device or emulator.

---

## Prerequisites

* **Android Studio**: Ladybug (2024.2+) or Meerkat (2024.3+)
* **Java Development Kit (JDK)**: JDK 17 or JDK 21
* **Android SDK**:
  * `compileSdk`: **36**
  * `targetSdk`: **36**
  * `minSdk`: **29** (Android 10+)
* **Hardware**: Android device or emulator running Android 10 (API 29) or higher with USB debugging enabled.
* **Server**: Reachable Chege Photos WebApp backend.

---

## 1. Build from Android Studio

1. Open Android Studio.
2. Select **File → Open** and choose the `Chege_Photos_App` project directory.
3. Wait for Gradle sync to download plugins and libraries defined in `gradle/libs.versions.toml`.
4. Select your target device/emulator from the device dropdown.
5. Click the **Run** button (green play icon `▶`) or press `Shift + F10`.

---

## 2. Command-Line Build & Installation (Gradle)

You can build and install the debug APK entirely via the command line:

### A. Compile Debug APK
```bash
cd path/to/Chege_Photos_App
./gradlew assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### B. Install onto Connected Device via ADB
Ensure your device or emulator is detected by `adb`:
```bash
adb devices
```

Install the APK directly:
```bash
./gradlew installDebug
# Or directly via adb:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### C. Launch the Application
```bash
adb shell am start -n com.niccher.chege_photos_app/.MainActivity
```

---

## Related Documentation

* [Configuration & Server Pairing Guide](configuration.md)
* [Troubleshooting Guide](troubleshooting.md)
* [Local Development & Build Variants](../engineering/local-development.md)
