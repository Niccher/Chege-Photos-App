# Architecture Overview — Chege Photos Android

The Chege Photos Android companion application is designed using modern Android architecture principles: a single-activity architecture, 100% Jetpack Compose UI, unidirectional data flow (UDF), and an offline-first repository pattern backed by Room and WorkManager.

---

## High-Level Architecture Diagram

```mermaid
graph TD
    subgraph ViewLayer["UI Layer (Jetpack Compose)"]
        MainActivity["MainActivity.kt (Single Activity)"]
        Screens["Compose Screens<br/>(Gallery, Lightbox, Albums, Faces, Memories, Settings)"]
        Components["Components & Overlays<br/>(Face Bounding Boxes, Stories Carousel)"]
    end

    subgraph DomainLayer["Repository & Coordination Layer"]
        Repo["PhotoRepository.kt"]
        Session["SessionManager.kt<br/>(Encrypted Auth Tokens & Server URL)"]
        Fingerprint["DeviceFingerprint.kt"]
    end

    subgraph LocalStorage["Local Persistence Layer"]
        RoomDB[("PhotoDatabase (Room)<br/>- CachedPhoto<br/>- OfflineAction")]
        MediaStore["Android MediaStore API<br/>(Local On-Device Camera Media)"]
    end

    subgraph BackgroundLayer["Background Work Engine"]
        WM["Android WorkManager"]
        SyncW["SyncWorker.kt"]
        OffW["OfflineSyncWorker.kt"]
        UpW["ManualUploadWorker.kt"]
    end

    subgraph NetworkLayer["Remote Network Engine"]
        Retrofit["Retrofit 2 + OkHttp 3<br/>(PhotoService.kt)"]
        Streaming["ContentUriRequestBody.kt<br/>(Okio Zero-Copy Socket Stream)"]
    end

    subgraph OnDeviceML["On-Device Intelligence"]
        MLKit["Google ML Kit<br/>(LocalFaceDetector.kt)"]
    end

    MainActivity --> Screens
    Screens --> Components
    Screens -->|State & Actions| Repo
    Repo --> RoomDB
    Repo --> Retrofit
    Repo --> MediaStore
    Repo --> MLKit

    WM --> SyncW
    WM --> OffW
    WM --> UpW
    SyncW --> Repo
    OffW --> Repo
    UpW --> Streaming
    Streaming --> Retrofit
    Retrofit -->|HTTPS REST| WebApp["Chege Photos WebApp Backend"]
```

---

## Architectural Layers

### 1. Presentation Layer (Jetpack Compose)
* **Single Activity**: `MainActivity` hosts all navigation states and screen composables.
* **Declarative Navigation**: Transitions between gallery grids, albums, search, face clusters, and full-screen image viewers are driven by Kotlin state hoisting.
* **Edge-to-Edge Insets**: Proper handling of `WindowInsetsCompat` and `statusBarsPadding()` ensures photos and action bars gracefully avoid hardware notches and camera cutouts.

### 2. Repository Layer (`PhotoRepository`)
* Serves as the single source of truth for the UI.
* Transparently merges server photos from Retrofit with local on-device images queried from Android's `MediaStore`.
* Automatically commits user actions (favoriting, deleting) to the Room database first for instantaneous UI responsiveness before scheduling background sync.

### 3. Background Synchronization (`WorkManager`)
* Coordinates deferred background synchronization respecting system constraints (Wi-Fi connected, battery not low, device idle).
* Survives app terminations and device reboots.

### 4. Zero-Copy Upload Streaming (`ContentUriRequestBody`)
* Directly bridges Android `ContentResolver` input streams to OkHttp network sockets via Okio.
* Prevents reading multi-gigabyte 4K video files or RAW images into JVM heap memory, completely eliminating Out-Of-Memory (OOM) crashes.

---

## Related Documentation

* [Inter-Service Communication & API Contracts](communication.md)
* [Data & Persistence Architecture (Room & MediaStore)](data-and-storage.md)
* [Android Services, WorkManager & Streaming](../services/android.md)
* [Making Changes & Adding Screens](../engineering/making-changes.md)
