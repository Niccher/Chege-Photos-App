# Engineering Troubleshooting Guide

Diagnostic procedures for Android Studio, Gradle JDK mismatches, emulator networking, and build failures encountered during local development.

---

## 1. Gradle JDK Version Mismatch

If Android Studio fails during Gradle sync with `Unsupported class file major version` or `Incompatible JVM`:

* **Required JDK**: JDK 17 (LTS) or JDK 21 (LTS).
* **Fix**:
  1. In Android Studio, navigate to **Settings / Preferences → Build, Execution, Deployment → Build Tools → Gradle**.
  2. Under **Gradle JDK**, select **Embedded JDK 17** or **jbr-17**.
  3. Re-sync the project via **File → Sync Project with Gradle Files**.

---

## 2. Missing `local.properties` or Android SDK

If building via command line fails with `SDK location not found`:

* Create `local.properties` in the project root:
  ```properties
  sdk.dir=/path/to/android/sdk
  ```
  *(Note: `local.properties` is machine-specific and gitignored; never commit it to source control).*

---

## 3. Emulator Loopback & Networking (`10.0.2.2`)

* The Android emulator operates behind an internal virtual router:
  - `10.0.2.2` maps to the development host's `127.0.0.1` (`localhost`).
  - Connecting to `http://localhost` or `http://localhost:80` inside the emulator connects to the emulator itself, NOT the host computer!
* **Resolution**: Configure server URL as `http://10.0.2.2` (port 80) in emulator dev builds.
* For physical USB-connected devices, use `adb reverse`:
  ```bash
  adb reverse tcp:80 tcp:80
  ```
  *(This forwards phone requests on `http://localhost` directly to the development workstation).*

---

## 4. Room Schema Export & Migration Verification

If Room fails compilation with `Room cannot verify the data integrity of your database`:

```bash
# Export schema directory check
# Ensure ksp arguments include room.schemaLocation in app/build.gradle.kts
./gradlew clean assembleDebug
```
