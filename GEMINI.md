# Chege Photos Android App — Agent Guidelines & Ground Truth

## 1. Ground Truth Architecture
* **App Role**: Native mobile companion client built with Jetpack Compose, Room, and WorkManager.
* **Target Platforms**: Android 10+ (API level 29 to 36).
* **Backend Connection**: The app communicates **strictly with Chege Photos WebApp** (`http://10.0.2.2:9005` in emulator, or host LAN IP e.g. `http://192.168.x.x:9005`).

## 2. Multi-Repo Boundaries
* **No Direct ML Connection**: The mobile client has ZERO direct network connections to the ML microservice or the Qdrant vector engine.
* **Delegated AI Features**: All ML features (face clustering, CLIP search, smart albums) are served through WebApp REST endpoints (`/api/v1/...`).

## 3. Privacy & Sanitization
* Never commit or document absolute personal filesystem paths (e.g. personal user directories).
* Never commit machine-specific paths in `gradle.properties` (e.g. local JDK paths).
* Use `$ANDROID_HOME`, `path/to/project`, or relative paths in all examples.
