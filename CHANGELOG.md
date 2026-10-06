# Changelog

All notable changes to the Chege Photos Android App will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.2.0] - 2026-10-06

### Added
- **15-Minute Background Auto-Backup**: Integrated WorkManager `PeriodicWorkRequestBuilder` for automated background sync with battery and unmetered network constraints.
- **Immediate Sync Trigger**: Added instant manual sync action button in Settings to discover and upload newly captured media immediately.
- **Video Discovery & Sync**: Added `MediaStore.Video.Media` query support alongside images for automated video discovery and backup.
- **Biometric Security**: Added `BiometricPrompt` fingerprint and face unlock protection for app access and private vault browsing.
- **Zero-Copy Streaming Uploads**: Implemented custom Okio `RequestBody` (`BufferedSink`) streaming directly from `ContentResolver` file descriptors to eliminate Out-Of-Memory (OOM) crashes on large media files and 4K videos.
- **Hybrid QR & 6-Digit PIN Pairing**: Added frictionless pairing via Google ML Kit barcode scanning with manual 6-digit numeric PIN fallback.
- **Multimodal CLIP Search**: Integrated natural-language search querying local CLIP vectors via WebApp REST endpoints (`/api/v1/search`).
- **People Carousel & Face Bounding Boxes**: Added people avatar carousel and `PersonPhotoPager` rendering real-time face bounding box overlays over photos.
- **Gallery Sorting & Metadata Badges**: Added remote photo sorting dropdown (date, size, name) and metadata overlays (resolution, EXIF, timestamps).
- **Live Progress Notifications**: Added real-time notification updates displaying byte progress and item counts (e.g. *"Uploading 4 of 400"*) with dedicated notification channel management.
- **Empty Trash Action**: Added 1-click empty trash with confirmation dialog and cascade purge.
- **Server Configuration Endpoint**: Added `/api/v1/server/config` handshake to dynamically respect server upload limits and photo editing permissions.

### Changed
- **Standard Port 80 Alignment**: Standardized default connection port to standard HTTP port `80` (`http://10.0.2.2` in emulator, LAN IP for physical devices), replacing legacy port `9005`.
- **Two-Repo Architecture**: Updated documentation and architecture to reflect the official two-repo arrangement (`Chege-Photos-App` companion client + `Chege-Photos-Platform` unified backend monorepo).
- **Version Parity**: Synchronized version numbering with Chege Photos Platform v1.2.0 release.

### Fixed
- **WorkManager 10KB Data Limit**: Resolved WorkManager payload serialization crash by moving from Intent/Data extras to a file-backed upload queue.
- **Upload Memory Leaks**: Eliminated heap buffer retention by streaming raw binary streams in 64 KB Okio chunks.
- **UI State Scoping**: Fixed CoroutineScope placement and `currentSort` scoping in `RemotePhotoListScreen`.
- **Session Cleanup**: Ensured complete wipe of local Room caches, tokens, and temporary files upon user logout.

---

## [1.1.0] - 2026-08-15

### Added
- **Room Schema v2**: Added offline action queue and vault flags to local Room SQLite database.
- **Full-Text Search (FTS)**: Integrated on-device SQLite FTS for rapid offline photo filtering.
- **Pull-to-Refresh**: Added pull-to-refresh gesture support across gallery views.

### Changed
- Migrated networking layer to Retrofit 2 with Kotlinx Serialization.

---

## [1.0.0] - 2026-07-01

### Added
- Initial release of the native Android companion app built with Jetpack Compose and Material 3.
- MediaStore integration for photo discovery.
- Token-based API authentication with Chege Photos WebApp.
- Local Room SQLite caching for gallery browsing.
