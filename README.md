# Open Photo Sync & Gallery

An open-source, full-stack, personal photo management ecosystem. It consists of a beautiful **Android application** written in modern Kotlin/Jetpack Compose, and a powerful **CodeIgniter 4 PHP Backend** that handles authentication, storage, and cross-device syncing.

Take control of your memories, host your own backend, sync photos securely from your mobile device, and browse your entire gallery—all offline-first, private, and yours.

---

## 🚀 Key Features

*   **Self-Hosted Privacy**: Point the app to your own self-hosted API server URL at login. No more recurring cloud fees or privacy invasions.
*   **Robust Synchronization**: Push your local device photos directly to the server with a powerful upload queue, supporting large file uploads with 5-minute timeouts.
*   **Beautiful Grid & Carousel Browsing**:
    *   2-Column dynamically sizing grid (`LazyVerticalGrid`).
    *   Tap any photo for an immersive, edge-to-edge swipeable Carousel view (`HorizontalPager`).
    *   Metadata overlays showing file names, sizes in MB, photo dimensions, and taken dates.
    *   **Cloud Download**: Download any remote image natively to a local `Prj Photos` public folder with one tap.
*   **Smart Categorization**: Browse via the Sidebar Navigation Drawer: Memories (last 12 months), Favorites, Archive, Trash, and the Map/Location-based Explore screen.
*   **Albums Support**: Server-managed Albums with dedicated detail views on the app.

---

## 🛠 Tech Stack

### Client (Android)
*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (`Material 3`, `MaterialIcons Extended`)
*   **Networking**: Retrofit 2, OkHttp 3
*   **Image Loading**: Coil Compose (for optimized remote thumbnail and full-res loading)
*   **Serialization**: Kotlinx Serialization (strict, fast JSON parsing)
*   **Architecture**: Single-Activity, Compose-Native, State-Hoisted MV architecture.

### Server (Backend)
*   **Framework**: CodeIgniter 4 (PHP 8+)
*   **Database**: MySQL / MariaDB
*   **Security**: CodeIgniter Shield for JWT/Bearer Token API Authentication.

---

## 📱 App Architecture (MainActivity Breakdown)

The entire application state and UI are anchored in `MainActivity.kt`. Here's a technical breakdown of the Compose UI structure:

*   **`MainScreen`**: The conductor. It wraps the application in a `ModalNavigationDrawer` (Sidebar) and a `Scaffold` with a Bottom `NavigationBar`. It manages the active state between main tabs (`Screen.Sync`, `Screen.Gallery`, `Screen.Albums`) and side tabs (`SidebarItem.Memories`, etc.).
*   **`LoginScreen`**: An unauthenticated state view allowing the user to provide a Dynamic Server URL, Email, and Password. Secures a Shield Auth Token and persists it via `SharedPreferences`.
*   **`SyncScreen`**: Queries the Android `MediaStore` for local images. Uses a `LazyVerticalGrid` to display local files, with a `Sync Now` batch-upload routine and individual `IconButton` upload triggers per photo.
*   **`RemotePhotoListScreen`**: A highly reusable Composable powering the Gallery and Sidebar categories. It accepts a Retrofit `suspend` lambda, fetches remote JSON data, lists them in a thumbnail Grid, and hosts the Fullscreen `HorizontalPager` carousel Dialog.
*   **`AlbumsScreen`**: Fetches the user's albums. Displays a list of cards with cover photos and total photo counts. Tapping a card delegates to `RemotePhotoListScreen` passing the targeted Album ID.

---

## 🔗 API Endpoints

The Android App communicates strictly over HTTPS using Bearer Token Authentication (CodeIgniter Shield `tokens` filter) to `/api/*` endpoints.

### Authentication
*   `POST /api/login`: Accepts `email`, `password`, `device_name`. Returns `access_token` and User Profile JSON.

### Retrieving Data
*   `GET /api/photos`: Fetches the unarchived master timeline of photos for the `GalleryScreen`.
*   `GET /api/albums`: Fetches the user's customized albums and photo counts.
*   `GET /api/albums/{id}/photos`: Fetches photos belonging to a specific album using the `album_photos` bridging table.
*   `GET /api/memories`, `/api/favorites`, `/api/archive`, `/api/trash`, `/api/explore`: Pre-filtered database queries isolating photos by timeline, boolean flags, or location tags.

### Uploading
*   `POST /api/upload`: Expects `multipart/form-data`. Key must be `file`. The server generates a thumbnail, extracts EXIF/Dimensions/Location metadata, and returns the successful `PhotoModel` ID.

---

## ⚙️ Setup & Installation

### 1. Server Setup
1. Clone the backend repository to your PHP server (Apache/Nginx).
2. Configure your MySQL database credentials in the `.env` file.
3. Run `php spark migrate` to build the required tables for `photos`, `albums`, `album_photos`, and Shield tables.
4. Ensure your `php.ini` permits large uploads:
    *   `upload_max_filesize = 50M`
    *   `post_max_size = 55M`
5. Ensure the `uploads/` and `thumbnails/` directories have writable `755` permissions.

### 2. App Setup
1. Open this Android project (`PrjPhotos`) in **Android Studio**.
2. Sync Gradle dependencies.
3. Ensure Android permissions `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` are declared in the Manifest.
4. Build the APK or run it on a Physical Device/Emulator.
5. On the Login screen, under "Server URL", enter the fully qualified domain of your completed Step 1 Server (e.g., `https://photos.yourdomain.com/`). Note the trailing slash!

## 🤝 Contributing
Open Photo Sync is an ever-evolving project. Feel free to fork, submit PRs to fix UI quirks, add new sorting methods, or build iOS clients against the existing API layout!
