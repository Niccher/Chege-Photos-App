# Ecosystem Release Matrix & Mobile Versioning

Release train management, `versionCode`/`versionName` conventions, Room database migrations, and WebApp API compatibility for Chege Photos Android App.

---

## 1. Versioning Standards

The Android app follows Semantic Versioning with monotonic build codes:

* **`versionCode`**: Monotonically increasing integer (e.g. `100`, `101`, `102`). Incremented on every internal build and release.
* **`versionName`**: Major.Minor.Patch string (e.g. `1.0.0`, `1.1.0`). Defined in `app/build.gradle.kts`.

---

## 2. WebApp Compatibility Matrix

| Android App Version | `versionCode` | Required WebApp API | Room Database Version |
|---|---|---|---|
| **v1.0.0** | 1 | `/api/v1` (Migrations 001–015) | Schema v1 |
| **v1.1.0** | 2 | `/api/v1` (Migrations 016–024) | Schema v2 (Adds vault flags & FTS) |
| **v1.2.0** | 3 | `/api/v1` (Platform v1.2.0) | Schema v2 (15-min auto-backup & video discovery) |

---

## 3. Room Schema Migration Rules

1. When modifying `@Entity` or adding tables, increment the database version in `AppDatabase.kt`.
2. Provide an explicit `Migration(from, to)` or configure `@AutoMigration` with necessary spec rules.
3. Commit the generated JSON schema in `schemas/com.niccher.chege_photos_app.data.local.AppDatabase/` to enable Room migration verification during CI builds.
