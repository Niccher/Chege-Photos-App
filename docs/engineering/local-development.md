# Local Development Guide — Chege Photos Android

This guide walks through configuring a local Android development workstation, syncing Gradle dependencies, running emulator instances, and building APKs.

---

## Workstation Prerequisites

* **Operating System**: Linux, macOS, or Windows
* **Java Development Kit (JDK)**: JDK 17 or JDK 21 (bundled with Android Studio or installed via system package manager)
* **Android Studio**: Ladybug (2024.2+) or Meerkat (2024.3+)
* **Android SDK Tools**:
  * Android SDK Platform: **API 36**
  * Android SDK Build-Tools: **36.0.0**
  * Android Emulator & Platform-Tools (`adb`)

---

## 1. Project Setup & Gradle Sync

### Clone Repository
```bash
cd /home/niccher/AndroidStudioProjects/Chege_Photos_App
```

### Configure Local Properties
If not already created, configure your Android SDK path in `local.properties`:
```properties
sdk.dir=/home/niccher/Android/Sdk
```

### Verify Gradle Sync & Compile
```bash
./gradlew compileDebugKotlin
```

---

## 2. Common Gradle Tasks

| Task | Command | Description |
|---|---|---|
| **Assemble Debug APK** | `./gradlew assembleDebug` | Compiles debug APK with full logging and debug symbols. |
| **Assemble Release APK** | `./gradlew assembleRelease` | Compiles minified release APK (requires keystore signing). |
| **Install on Device** | `./gradlew installDebug` | Builds and installs APK onto connected emulator or device. |
| **Run Unit Tests** | `./gradlew testDebugUnitTest` | Runs local JVM unit tests. |
| **Run Android Lint** | `./gradlew lint` | Generates Android Lint code quality inspection report. |
| **Clean Build Cache** | `./gradlew clean` | Purges intermediate build directories (`app/build/`). |

---

## 3. Running with ADB & Emulator

### List Connected Emulators / Devices
```bash
adb devices
```

### Install and Launch Debug Build
```bash
./gradlew installDebug
adb shell am start -n com.niccher.chege_photos_app/.MainActivity
```

### Real-Time Logcat Monitoring
```bash
adb logcat -s "ChegePhotos:*" "WorkManager:*" "OkHttp:*"
```

---

## Related Documentation

* [Making Changes & Definition of Done](making-changes.md)
* [Testing Guide](testing.md)
* [Architecture Overview](../architecture/overview.md)
