# Chege Photos Android App — Engineering Handbook

Welcome to the engineering documentation for the Chege Photos Android companion app. This documentation is intended for mobile software engineers building, maintaining, and enhancing the native Kotlin client.

If you only need to build and install the APK or connect the app to your server, see the [Root README](../README.md).

---

## Documentation Navigation

| I want to… | Go here |
|---|---|
| **Build and install the APK without reading source code** | [../README.md](../README.md) |
| **Understand app architecture & MVVM clean layout** | [architecture/overview.md](architecture/overview.md) |
| **Inspect REST API contracts, auth & sync protocols** | [architecture/communication.md](architecture/communication.md) |
| **Review Room SQLite database & Scoped Storage caching** | [architecture/data-and-storage.md](architecture/data-and-storage.md) |
| **Inspect Compose UI, Okio streaming & WorkManager** | [services/android.md](services/android.md) |
| **Set up Android Studio, SDK 36 & build variants** | [engineering/local-development.md](engineering/local-development.md) |
| **Add a Compose screen, Room migration, or endpoint** | [engineering/making-changes.md](engineering/making-changes.md) |
| **Execute unit and instrumented tests** | [engineering/testing.md](engineering/testing.md) |
| **Pair the app with a server (QR code / manual URL)** | [user/configuration.md](user/configuration.md) |
| **Troubleshoot cleartext HTTP, permissions & sync** | [user/troubleshooting.md](user/troubleshooting.md) |

---

## Sibling Repositories

| Repository | Responsibility | Tech Stack |
|---|---|---|
| **[Chege-Photos-Android](https://github.com/niccher/Chege-Photos-Android)** | Native Mobile Companion Client | Kotlin / Jetpack Compose / Room / WorkManager |
| **[Chege-Photos-WebApp](https://github.com/niccher/Chege-Photos-WebApp)** | Core Web UI, Admin, Auth & Mobile Sync | PHP 8.3 / CodeIgniter 4 / MySQL |
| **[Chege-Photos-ML](https://github.com/niccher/Chege-Photos-ML)** | Face Detection, YOLOv8, CLIP & Qdrant | Python 3.12 / FastAPI / PyTorch |
