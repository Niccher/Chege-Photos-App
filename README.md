# Chege Photos Android App

Native Kotlin Android companion application for the Chege Photos self-hosted photo management platform, built with Jetpack Compose, Room offline caching, and WorkManager background sync.

**Stack**: Kotlin 2.0, Jetpack Compose, Material 3, Room, WorkManager, Retrofit 2, OkHttp 3, Okio, ML Kit.  
**Audience**: If you only need to build and install the companion APK, this page is enough. Mobile engineers: [docs/README.md](docs/README.md).

---

## What “Running” Looks Like

| Piece | URL / How to open | Purpose / Status |
|---|---|---|
| **Companion App** | Android Device / Emulator | Primary mobile client for media browsing and cloud backup |
| **Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | Generated debug binary for local testing |
| **Server Pairing** | Desktop QR Code / Manual URL | Authenticates device via hardware fingerprint and pairing token |
| **Logcat Stream** | `adb logcat -s "ChegePhotos:*"` | Real-time diagnostic and sync telemetry |

---

## Prerequisites

* **Android Studio**: Ladybug (2024.2+) or Meerkat (2024.3+)
* **Java Development Kit (JDK)**: JDK 17 or JDK 21
* **Android Target**: Android 10+ (API level 29 or higher)
* **Backend**: Running instance of [Chege Photos WebApp](https://github.com/niccher/Chege-Photos-WebApp) (e.g. `http://10.0.2.2:9005` for emulators, or LAN IP for physical devices).  
  *(Note: The Android app talks exclusively to the WebApp; it does not connect directly to the ML microservice — the WebApp coordinates all AI indexing behind the scenes).*

---

## Setup and Run

### Option A — Android Studio (Recommended)

1. Launch Android Studio.
2. Select **File → Open** and choose the `Chege_Photos_App` directory.
3. Allow Gradle to finish syncing dependencies.
4. Select your connected device or emulator from the device dropdown and click **Run (▶)**.

### Option B — Command Line (Gradle & ADB)

```bash
# 1. Compile debug APK
./gradlew assembleDebug

# 2. Install directly onto connected device/emulator
./gradlew installDebug

# Or install manually via ADB:
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Launch application
adb shell am start -n com.niccher.chege_photos_app/.MainActivity
```

---

## Configuration

### Pairing with Your Server

1. Open your **Chege Photos WebApp** in a desktop browser.
2. Navigate to **User Profile / Settings → Mobile Companion App** and click **Generate Pairing Token**.
3. Open the Chege Photos Android app and tap **Scan QR Code to Connect**.
4. Scan the desktop screen code to instantly link your device.  
   *(Alternatively, tap **Enter Server URL Manually** to input your server domain or local IP).*

For detailed sync preferences, battery limits, and Wi-Fi options, see [docs/user/configuration.md](docs/user/configuration.md).

---

## Troubleshooting

* **Cleartext HTTP Blocked**: Android 9+ requires HTTPS or explicit network security exceptions for local IPs.
* **Camera Opens Blank**: Grant Camera permission in Android Settings → Apps → Chege Photos.
* **Background Sync Delayed**: Set battery usage to **Unrestricted** to prevent system Doze throttling.

Detailed diagnostic steps and recovery procedures: [docs/user/troubleshooting.md](docs/user/troubleshooting.md).

---

## Engineering Documentation

For architecture, Room database schemas, streaming upload pipelines, and developer workflows, see the **[Engineering Handbook](docs/README.md)**:

* [Architecture Overview](docs/architecture/overview.md)
* [Network & API Communication](docs/architecture/communication.md)
* [Data & Storage (Room SQLite & MediaStore)](docs/architecture/data-and-storage.md)
* [Android Services (Okio Streaming & WorkManager)](docs/services/android.md)
* [Local Development & Gradle Tasks](docs/engineering/local-development.md)
* [Making Changes & Definition of Done](docs/engineering/making-changes.md)
* [Testing Guide](docs/engineering/testing.md)

---

## Ecosystem & Multi-Repo Architecture

```
[ Chege Photos Android ]
         │
         │ (HTTPS / Bearer Token - port 9005)
         ▼
[ Chege Photos WebApp ] (Port 9005) ─── MySQL 8.4 (Port 9306)
         │
         │ (Internal HTTP / X-API-KEY - port 9051)
         ▼
[ ML Chege Photos ] (Port 9051) ─────── Qdrant Vector DB (Port 9052)
```

### Architecture for Android Developers
* **Direct Connection**: The Android companion client communicates **only with the WebApp**.
* **Zero Direct ML Dependency**: Android never needs direct network access or credentials for the ML microservice or Qdrant vector database.
* **Coordinated Features**: All ML-powered capabilities (face groupings, smart albums, CLIP semantic search) are requested through WebApp REST endpoints (`/api/v1/...`), which orchestrates them transparently.

---

## Sibling Repositories

| Repository | Responsibility | Tech Stack |
|---|---|---|
| **[Chege-Photos-Android](https://github.com/niccher/Chege-Photos-Android)** | Native Mobile Companion Client | Kotlin / Jetpack Compose |
| **[Chege-Photos-WebApp](https://github.com/niccher/Chege-Photos-WebApp)** | Core Web UI, Admin, Auth & Mobile Sync | PHP 8.3 / CodeIgniter 4 |
| **[Chege-Photos-ML](https://github.com/niccher/Chege-Photos-ML)** | Face Detection, YOLOv8, CLIP & Qdrant | Python 3.12 / FastAPI |

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
