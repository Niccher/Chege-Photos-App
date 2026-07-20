# Chege Photos

A Kotlin Android app (Jetpack Compose) for Chege Photos — a self-hosted photo management platform. Syncs with the web backend and features ML-powered face recognition.

## Tech Stack

| Category | Libraries |
|---|---|
| **Language** | Kotlin 2.0.21 |
| **UI** | Jetpack Compose (Material 3, BOM 2024.09.00), Material Icons Extended |
| **Networking** | Retrofit 2.11.0, OkHttp 4.12.0, OkHttp Logging Interceptor |
| **Image Loading** | Coil 2.6.0 (Compose integration) |
| **Async** | Kotlin Coroutines, lifecycle-runtime-ktx |
| **Local DB** | Room 2.6.1 (with KSP) |
| **Serialization** | Kotlinx Serialization JSON 1.7.3, Retrofit converter for kotlinx.serialization |
| **Camera** | CameraX 1.4.1 (core, camera2, lifecycle) |
| **Barcode** | ML Kit Barcode Scanning 17.3.0 |
| **Biometrics** | AndroidX Biometric 1.1.0 |
| **Build** | AGP 8.9.1, Gradle 9.6.1, KSP 2.0.21-1.0.27 |
| **JDK** | 11 (source/target compatibility) |

## Architecture

Single-activity architecture (`MainActivity` extends `FragmentActivity`). All UI is built with Compose and navigation is handled via state — a `currentScreen` variable controls which composable is displayed. There is no Jetpack Navigation Component. The app connects to the Chege Photos web API via Retrofit, using an `ApiClient` singleton that manages an OkHttp client with automatic Bearer token injection, trust-all SSL (for self-hosted servers), and URL normalisation. A `PhotoRepository` layer bridges the network layer and a Room database for offline caching.

### Navigation

- **Bottom bar tabs**: Sync, Gallery, Albums
- **Sidebar / drawer items**: Profile, Memories, Favorites, Archive, Trash, Explore, Faces, Theme, Server Config, About

The sidebar exposes a Login screen when unauthenticated; after login the main scaffold with bottom bar and drawer is shown.

## Why ML over Heuristics

Face recognition is performed server-side using Insightface (Buffalo-L model) for detection and embedding generation, with Qdrant as the vector database for similarity search. The app consumes the results via REST endpoints.

| Approach | Heuristic grouping | ML-based (Insightface + Qdrant) |
|---|---|---|
| **Pose/lighting** | Fails on non-frontal faces, varied lighting | Robust to pose, expression, occlusion, illumination |
| **Embedding** | None (relies on EXIF, time, manual tags) | Consistent 512-dimensional embeddings |
| **Search speed** | Linear scan, no indexing | Sub-second similarity search across the entire library |
| **Clustering** | Requires manual tagging or folder organisation | Automatic clustering by embedding distance |

The server-side pipeline detects faces, computes embeddings via Buffalo-L, indexes them in Qdrant, and returns face metadata (bounding box, person ID, confidence score, age, gender). The app uses these annotations to power the Faces screen, photo detail overlay, and face search.

## How to Set Up

### Prerequisites

- Android Studio (latest stable)
- JDK 11+
- Gradle 9.6 (bundled wrapper)

### Steps

1. Open the project in Android Studio.
2. Wait for Gradle sync to complete.
3. Build and run on a device / emulator (minSdk 29).
4. On first launch, enter your server URL in the **Server Config** screen (accessible from the sidebar). The default is `https://photos.chegecache.co.ke/`.
5. Log in with your Chege Photos credentials.

The app auto-detects private IP ranges and local hostnames, using `http://` for local servers and `https://` for public ones.

## Key Features

### Sync
Scans the device's `MediaStore` for local photos and uploads them to the server with device fingerprint tracking. Upload progress is reported via a callback. Notifications are posted for file transfers.

### Gallery
Browses remote photos with a search bar and type filter (All / JPG / PNG / MP4). Supports long-press multi-select for batch operations (favorite, archive, delete, download, add to album).

### Albums
Lists remote photo albums. Supports creating, editing, and deleting albums. Photos can be added to albums via the multi-select action bar in any photo list.

### Faces (Face Search)
A persons grid displaying detected faces grouped by person. Each person card shows a face thumbnail. Tapping a person loads all photos containing that person via `GET /api/faces/by-person/{id}`.

### Photo Detail
Fullscreen carousel with pinch-to-zoom and pan. Swipe left/right to navigate photos. Tapping toggles face bounding box overlays (fetched from `GET /api/faces/{photoId}`). An info bottom sheet shows EXIF metadata and a list of detected faces with person names.

## API Endpoints Used

| Endpoint | Description |
|---|---|
| `POST /api/login` | Email/password login |
| `POST /api/auth-with-token` | Token-based authentication with device fingerprint |
| `GET /api/photos` | List remote photos |
| `GET /api/albums` | List albums |
| `GET /api/albums/{id}/photos` | Photos in an album |
| `POST /api/upload` | Upload a photo (multipart) |
| `POST /photos/delete/{id}` | Delete photo |
| `POST /photos/restore/{id}` | Restore photo from trash |
| `POST /photos/archive/{id}` | Archive photo |
| `POST /photos/favorite/{id}` | Favorite photo |
| `POST /api/albums` | Create album |
| `PUT /api/albums/{id}` | Update album |
| `DELETE /api/albums/{id}` | Delete album |
| `POST /albums/add-photo` | Add photo to album |
| `GET /api/memories` | Memories feed |
| `GET /api/favorites` | Favorited photos |
| `GET /api/archive` | Archived photos |
| `GET /api/trash` | Trashed photos |
| `GET /api/explore` | Explore / public feed |
| `GET /api/faces/{photoId}` | Detected faces for a photo |
| `POST /api/faces/search` | Upload a photo and search for matching faces |
| `GET /api/faces/persons` | List all detected persons |
| `GET /api/faces/by-person/{personId}` | All photos containing a specific person |

## Dependencies

All key libraries (from `gradle/libs.versions.toml` and `app/build.gradle.kts`):

```
androidx.core:core-ktx:1.17.0
androidx.lifecycle:lifecycle-runtime-ktx:2.9.4
androidx.activity:activity-compose:1.12.3
androidx.compose:compose-bom:2024.09.00
androidx.compose.ui:ui
androidx.compose.ui:ui-graphics
androidx.compose.ui:ui-tooling-preview
androidx.compose.material3:material3
androidx.compose.material:material-icons-extended
com.squareup.retrofit2:retrofit:2.11.0
com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0
com.squareup.okhttp3:okhttp:4.12.0
com.squareup.okhttp3:logging-interceptor:4.12.0
org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3
io.coil-kt:coil-compose:2.6.0
androidx.biometric:biometric:1.1.0
androidx.camera:camera-core:1.4.1
androidx.camera:camera-camera2:1.4.1
androidx.camera:camera-lifecycle:1.4.1
androidx.camera:camera-view:1.6.1
com.google.mlkit:barcode-scanning:17.3.0
androidx.room:room-runtime:2.6.1
androidx.room:room-ktx:2.6.1
```

## Permissions

```xml
INTERNET
CAMERA
READ_EXTERNAL_STORAGE (maxSdkVersion 32)
READ_MEDIA_IMAGES
READ_MEDIA_VIDEO
POST_NOTIFICATIONS
```

Biometric authentication is handled via `androidx.biometric` (no manifest permission required).

## Build Configuration

```
minSdk     = 29
targetSdk  = 36
compileSdk = 36
Gradle     = 9.6.1
AGP        = 8.9.1
```

## Package Structure

```
com.niccher.chege_photos_app/
├── data/
│   └── PhotoDatabase.kt          # Room database, DAO, migrations
├── models/
│   ├── AlbumResponse.kt          # Album, AlbumListResponse, SingleAlbumResponse
│   ├── AuthResponse.kt           # AuthResponse, UserInfo
│   ├── CachedPhoto.kt            # Room entity + converters
│   ├── FaceData.kt               # FaceData, FaceSearchResult, PersonData, responses
│   └── Photo.kt                  # Photo, PhotoListResponse
├── network/
│   ├── ApiClient.kt              # Retrofit singleton, OkHttp client, URL normalisation
│   └── PhotoService.kt           # Retrofit interface (all API endpoints)
├── repository/
│   └── PhotoRepository.kt        # Network + cache logic, upload/download helpers
├── ui/
│   └── theme/
│       ├── Color.kt              # Color definitions
│       ├── Theme.kt              # ChegePhotosTheme, AppTheme enum (5 themes)
│       └── Type.kt               # Typography
├── utils/
│   ├── DeviceFingerprint.kt      # Device ID generation
│   ├── ProgressRequestBody.kt   # Upload progress tracking
│   └── SessionManager.kt        # Auth token, user prefs, biometric state, theme pref
├── MainActivity.kt               # Single activity, all screens, navigation
└── QrScannerActivity.kt          # ML Kit barcode scanner activity
```
