<div align="center">

# Chege Photos

Android companion app for the Chege Photos self-hosted photo management platform.

![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin)
![Compose](https://img.shields.io/badge/Compose-BOM_2024.09.00-4285F4?style=for-the-badge&logo=jetpackcompose)
![minSdk](https://img.shields.io/badge/minSdk-29-34A853?style=for-the-badge&logo=android)
![targetSdk](https://img.shields.io/badge/targetSdk-36-34A853?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)

</div>

---

## About the Project

Chege Photos is a Kotlin Android app built with Jetpack Compose that syncs with the [Chege Photos WebApp](https://github.com/niccher/Chege-Photos-WebApp) backend. It provides photo upload/sync, browsing, album management, media organisation (archive, trash, favorites), a shared/public explore feed, and ML-powered face recognition via the [ML Chege Photos](https://github.com/niccher/Chege-Photos-ML) service. All UI is built in a single-activity Compose architecture with state-based navigation — no Jetpack Navigation Component.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        Android App                            │
│                                                              │
│  ┌──────────────┐   Retrofit    ┌────────────────────────┐   │
│  │  Single Activity            │  Chege Photos Web App   │   │
│  │  (MainActivity)             │  (PHP/CI4, port 9005)   │   │
│  │                    │         │                          │   │
│  │  ├─ Compose UI    │─────────▶  ├─ REST API             │   │
│  │  ├─ PhotoRepository │        │  ├─ Auth (tokens)       │   │
│  │  ├─ Room Cache     │        │  └─ Face proxy          │   │
│  │  └─ SessionManager │         └───────────┬──────────────┘   │
│  └─────────────────────┘                     │                   │
│            │                                 │                   │
│            │  HTTPS                           │ cURL             │
│            ▼                                 ▼                   │
│  ┌──────────────────┐            ┌────────────────────────┐   │
│  │  ML Chege Photos  │            │  MySQL 8.4              │   │
│  │  (FastAPI)        │            │  (db_chege_photos)      │   │
│  │  port 9051        │            │  port 3306              │   │
│  └──────────────────┘            └────────────────────────┘   │
│                                                              │
│  ┌──────────────────┐                                        │
│  │  Qdrant           │  Vector DB for face embeddings        │
│  │  (ANN search)    │                                        │
│  └──────────────────┘                                        │
└──────────────────────────────────────────────────────────────┘
```

The app communicates with the web backend via Retrofit 2 (with kotlinx.serialization). Authentication is handled via Shield auth tokens (8-character codes displayed as QR codes in the web UI). Face data is fetched directly from the web app's face API endpoints, which in turn proxy to the ML service. A Room database caches gallery photo metadata for offline access (other feeds are fetched live).

---

## Features

### Sync & Upload
- **MediaStore scan** — Scans device's `MediaStore.Images.Media` for local photos
- **Batch upload** — Uploads local photos to server with per-file progress callbacks and device fingerprint tracking
- **Notification** — Upload progress posted as Android notification
- **Auto-retry** — Failed uploads can be retried from the sync screen

### Gallery & Browsing
- **Remote photo grid** — Paginated grid with infinite scroll from `GET /api/photos`
- **Multi-select** — Long-press to enter selection mode; batch favorite, archive, delete, download, add-to-album
- **Fullscreen viewer** — Carousel with pinch-to-zoom and pan; swipe left/right between photos
- **Search & filter** — Search bar and type filter chips (All / JPG / PNG / MP4) in gallery view

### Albums
- **List** — All remote albums with photo count and cover image
- **CRUD** — Create, rename, delete albums
- **Add photos** — From multi-select action bar in any photo list

### Faces (requires ML service)
- **Person grid** — All detected persons with face thumbnails; tap to view photos containing that person
- **Person photo viewer** — Grid of photos for a selected person; tap to open fullscreen pager
- **Person photo pager** — Horizontal swipe through all person photos with:
  - Pinch-to-zoom and pan gestures
  - Face bounding box overlay toggle (gold = current person, green = other persons)
  - Photo counter and close button
- **Photo detail overlay** — Tapping the photo info button shows a bottom sheet with face list, person names, and EXIF metadata
- **Face search** — Upload a photo to find matching faces across the library

### Memories, Explore, Archive, Trash, Favorites
- **Memories** — On-this-day and 6-months-ago feed (two feeds served by the backend)
- **Explore** — Public / shared photo feed
- **Archive** — Archived photos (tap to view, with option to unarchive)
- **Trash** — Soft-deleted photos (with restore option)
- **Favorites** — Filtered view of favorited photos

### Sharing
- **Share intents** — Supports `ACTION_SEND` and `ACTION_SEND_MULTIPLE` for images and videos from other apps
- **Upload dialog** — Incoming share intents show a dialog to select target album before uploading

### Authentication & Security
- **Email/password login** — Standard credential login via `POST /api/login`. A default superuser profile is seeded automatically for administrative overrides (`superadmin@eavesdroid.com` / `SuperAdmin@2024!`).
- **Token login** — 8-character auth token (from web settings) + device fingerprint via `POST /api/auth-with-token`
- **QR scan** — Scan the QR code from the web app's token page using CameraX + ML Kit barcode scanner
- **Biometric unlock** — Optional biometric gate on app launch using AndroidX Biometric
- **Device fingerprinting** — SHA-256 of ~20 Build fields for device identification

### Themes
- **5 themes** — Default (dynamic color on Android 12+), Solarized, Grey, Midnight, Black
- **Persistence** — Theme selection stored in SharedPreferences, applied on next launch

---

## Tech Stack

| Category | Libraries (exact versions) |
|---|---|
| **Language** | Kotlin 2.0.21 |
| **UI** | Jetpack Compose (BOM 2024.09.00), Material 3, Material Icons Extended |
| **Networking** | Retrofit 2.11.0, OkHttp 4.12.0, OkHttp Logging Interceptor |
| **Image loading** | Coil 2.6.0 (Compose integration) |
| **Serialization** | Kotlinx Serialization JSON 1.7.3 |
| **Local DB** | Room 2.6.1 (with KSP 2.0.21-1.0.27) |
| **Camera** | CameraX 1.4.1 (core, camera2, lifecycle, view 1.6.1) |
| **Barcode** | ML Kit Barcode Scanning 17.3.0 |
| **Biometrics** | AndroidX Biometric 1.1.0 |
| **Build** | AGP 8.9.1, Gradle 9.6.1 |

## Prerequisites

- Android Studio Ladybug or later
- JDK 11+
- Gradle 9.6.1 (bundled wrapper)
- Android device / emulator running Android 10+ (minSdk 29)

## Installation & Setup

```bash
# Open the project in Android Studio
# Wait for Gradle sync to complete
# Build and run on device / emulator
```

### Connecting to a Backend

1. **Default URL:** `https://photos.chegecache.co.ke/` (points to the public staging demo host).
2. **Custom URL:** Change the server URL in the **Server Config** screen (sidebar → Server Config). The app includes URL normalisation that auto-detects private IP ranges (`10.x`, `172.16-31.x`, `192.168.x`, `localhost`, `*.local`) and uses `http://` for local servers, `https://` for public ones.
3. **Using an emulator:** Use `http://10.0.2.2:9005/` to reach the host machine's self-hosted Docker web app.
4. **Using a physical device:** Use `http://<host-ip>:9005/` (ensure both device and host are on the same network to connect to your self-hosted Docker container).

### Generating an Auth Token

1. Log in to the web app at your server URL
2. Go to **Settings → Tokens**
3. Click **Generate New Token** — an 8-character token is created
4. Either scan the QR code with the Android app's QR scanner, or manually enter the token

---

## Usage / API Endpoints

### Authentication

| Method | Retrofit function | Endpoint | Purpose |
|---|---|---|---|
| `POST` | `login()` | `api/login` | Email/password login |
| `POST` | `authWithToken()` | `api/auth-with-token` | Token + device fingerprint auth |

### Photo browsing

| Method | Retrofit function | Endpoint | Purpose |
|---|---|---|---|
| `GET` | `getRemotePhotos()` | `api/photos` | List all remote photos |
| `GET` | `getMemories()` | `api/memories` | Memories feed |
| `GET` | `getFavorites()` | `api/favorites` | Favorited photos |
| `GET` | `getArchived()` | `api/archive` | Archived photos |
| `GET` | `getTrash()` | `api/trash` | Trashed photos |
| `GET` | `getExplore()` | `api/explore` | Explore feed |

### Albums

| Method | Retrofit function | Endpoint | Purpose |
|---|---|---|---|
| `GET` | `getAlbums()` | `api/albums` | List albums |
| `GET` | `getAlbumPhotos(id)` | `api/albums/{id}/photos` | Photos in album |
| `POST` | `createAlbum()` | `api/albums` | Create album |
| `PUT` | `updateAlbum()` | `api/albums/{id}` | Update album |
| `DELETE` | `deleteAlbum()` | `api/albums/{id}` | Delete album |
| `POST` | `addPhotoToAlbum()` | `albums/add-photo` | Add photo to album |

### Photo actions

| Method | Retrofit function | Endpoint | Purpose |
|---|---|---|---|
| `POST` | `uploadPhoto()` | `api/upload` | Upload photo (multipart) |
| `POST` | `deletePhoto()` | `photos/delete/{id}` | Soft-delete photo |
| `POST` | `restorePhoto()` | `photos/restore/{id}` | Restore from trash |
| `POST` | `archivePhoto()` | `photos/archive/{id}` | Archive photo |
| `POST` | `favoritePhoto()` | `photos/favorite/{id}` | Toggle favorite |

### Face recognition

| Method | Retrofit function | Endpoint | Purpose |
|---|---|---|---|
| `GET` | `getFacesByPhoto(photoId)` | `api/faces/{photoId}` | Faces for a photo |
| `POST` | `searchFacesByPhoto()` | `api/faces/search` | Upload photo + search faces |
| `GET` | `getPersons()` | `api/faces/persons` | List all persons |
| `GET` | `getPersonPhotos(personId)` | `api/faces/by-person/{personId}` | Photos containing a person |

---

## Project Structure

```
Chege_Photos_App/
├── app/
│   ├── build.gradle.kts             # App module: SDK versions, dependencies
│   └── src/main/
│       ├── AndroidManifest.xml       # Permissions, activities, intent filters
│       ├── java/com/niccher/chege_photos_app/
│       │   ├── MainActivity.kt       # Single activity, all composable screens (3400+ lines)
│       │   ├── QrScannerActivity.kt   # CameraX + ML Kit barcode scanner
│       │   ├── data/
│       │   │   └── PhotoDatabase.kt  # Room database, DAO, migrations
│       │   ├── models/
│       │   │   ├── Photo.kt          # Photo, PhotoListResponse
│       │   │   ├── AlbumResponse.kt  # Album, AlbumListResponse, SingleAlbumResponse
│       │   │   ├── AuthResponse.kt   # AuthResponse, UserInfo
│       │   │   ├── FaceData.kt       # FaceData, PersonData, PersonPhoto, search models
│       │   │   └── CachedPhoto.kt    # Room entity ↔ Photo converter
│       │   ├── network/
│       │   │   ├── ApiClient.kt      # Retrofit singleton, OkHttp, URL normalisation
│       │   │   └── PhotoService.kt   # Retrofit interface (all 24 endpoints)
│       │   ├── repository/
│       │   │   └── PhotoRepository.kt # Network + Room cache, upload/download logic
│       │   ├── ui/theme/
│       │   │   ├── Color.kt          # Color definitions (light, solarized, grey, midnight, black)
│       │   │   ├── Theme.kt          # ChegePhotosTheme, 5 AppTheme variants
│       │   │   └── Type.kt           # Typography
│       │   └── utils/
│       │       ├── SessionManager.kt # SharedPreferences: auth, theme, biometric, user prefs
│       │       ├── DeviceFingerprint.kt # SHA-256 build-field fingerprint
│       │       └── ProgressRequestBody.kt # OkHttp upload progress wrapper
│       └── res/                      # Drawables, layouts, strings, themes, XML config
├── gradle/
│   ├── libs.versions.toml            # Version catalog (all dependency versions)
│   └── wrapper/
│       └── gradle-wrapper.properties # Gradle 9.6.1
├── build.gradle.kts                  # Project-level: AGP, Kotlin, KSP plugins
├── settings.gradle.kts               # Module includes, repository config
├── gradle.properties                 # JVM args, AndroidX
├── local.properties                  # SDK path (machine-local)
├── CONTRIBUTING.md
└── LICENSE                           # MIT
```

---

## Screens

| Screen | Composable | Description |
|---|---|---|
| Login | `LoginScreen` | Email/password + token login with QR scanner |
| Sync | `SyncScreen` | Local photo scan, batch upload with progress |
| Gallery | `GalleryScreen` → `RemotePhotoListScreen` | Remote photo grid with multi-select |
| Albums | `AlbumsScreen` | Album list + CRUD |
| Album detail | *(inline in AlbumsScreen)* | Photos in an album |
| Faces | `FaceSearchScreen` | Person grid with thumbnails |
| Person photos | `PersonPhotosScreen` | Photos of a person + fullscreen pager |
| Memories | `RemotePhotoListScreen` | On-this-day and 6-month feeds |
| Favorites | `RemotePhotoListScreen` | Favorited photos |
| Archive | `RemotePhotoListScreen` | Archived photos |
| Trash | `RemotePhotoListScreen` | Soft-deleted photos |
| Explore | `RemotePhotoListScreen` | Shared/public feed |
| Profile | `ProfileScreen` | User details, biometric toggle |
| Server Config | `ServerConfigScreen` | Backend URL, connection test |
| About | `AboutScreen` | App version, how-it-works, libraries |
| QR Scanner | `QrScannerActivity` | CameraX + ML Kit barcode scanner |
| Photo detail | *(fullscreen dialog)* | Carousel, zoom/pan, face overlays, info sheet |

---

## Permissions

```xml
INTERNET
CAMERA
READ_EXTERNAL_STORAGE          (maxSdkVersion 32)
READ_MEDIA_IMAGES
READ_MEDIA_VIDEO
POST_NOTIFICATIONS
```

Biometric authentication requires no manifest permission.

---

## Configuration

The default backend URL is hardcoded in `ApiClient.kt` as `https://photos.chegecache.co.ke/`. Users can override it at runtime via the Server Config screen; the custom URL is persisted to SharedPreferences under the key `server_url`.

| Setting | Key | Default |
|---|---|---|
| Server URL | `server_url` | `https://photos.chegecache.co.ke/` |
| Theme | — | `DEFAULT` |
| Biometric enabled | `biometric_enabled` | `false` |

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

---

## License

MIT License. See `LICENSE` file in this repository.

---

## Support / Contact

For issues and feature requests, please open an issue on the [GitHub repository](https://github.com/niccher/Chege-Photos-Android/issues).
