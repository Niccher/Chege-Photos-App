# Making Changes & Definition of Done — Chege Photos Android

This document provides developer guidelines for adding Compose screens, updating the Room database schema, adding API endpoints, and fulfilling the project Definition of Done (DoD).

---

## 1. Adding a New Jetpack Compose Screen

1. **Create Screen Composable**:
   Create the UI file under `app/src/main/java/com/niccher/chege_photos_app/ui/screens/` or directly inside `MainActivity.kt`.
2. **Apply Window Insets**:
   Always wrap top-level containers in `.statusBarsPadding()` and `.navigationBarsPadding()` to ensure edge-to-edge compatibility with camera notches and gesture pills.
3. **Bind State to Repository**:
   Consume data via Kotlin coroutine `Flow` or `StateFlow` exposed by [PhotoRepository.kt](../../app/src/main/java/com/niccher/chege_photos_app/repository/PhotoRepository.kt).
4. **Wire Navigation State**:
   Add the destination to the navigation state enum/sealed class inside `MainActivity.kt`.

---

## 2. Modifying Room Database Schema & Migrations

When altering database entities (e.g. adding a column to `CachedPhoto`):

1. **Update Entity Model**:
   Edit the entity class in [app/src/main/java/com/niccher/chege_photos_app/models/](../../app/src/main/java/com/niccher/chege_photos_app/models/).
2. **Increment Database Version**:
   In [PhotoDatabase.kt](../../app/src/main/java/com/niccher/chege_photos_app/data/PhotoDatabase.kt), bump `@Database(version = X, ...)` by 1.
3. **Write Migration Object**:
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(database: SupportSQLiteDatabase) {
           database.execSQL("ALTER TABLE cached_photos ADD COLUMN camera_model TEXT")
       }
   }
   ```
4. **Register Migration in Builder**:
   ```kotlin
   Room.databaseBuilder(context, PhotoDatabase::class.java, "photo_database")
       .addMigrations(MIGRATION_1_2)
       .build()
   ```

---

## 3. Adding a Remote API Endpoint

1. **Define Endpoint Signature**:
   Add the HTTP method and path in [PhotoService.kt](../../app/src/main/java/com/niccher/chege_photos_app/network/PhotoService.kt).
2. **Implement Response Schema**:
   Add Kotlinx `@Serializable` data classes in `models/` matching the server JSON payload.
3. **Expose in Repository**:
   Wrap the Retrofit call inside `PhotoRepository.kt` with error handling and fallback cache updates.

---

## 4. Definition of Done (DoD) Checklist

Before submitting a Pull Request:

- [ ] **Compilation**: App compiles without warnings (`./gradlew compileDebugKotlin`).
- [ ] **Edge-to-Edge Compliance**: New UI layouts properly handle system window insets without content clipping.
- [ ] **Memory Safety**: No bulk byte arrays created in heap memory; large file transfers use `ContentUriRequestBody`.
- [ ] **Room Migration Verified**: Any database entity modification includes a verified, tested `Migration` block.
- [ ] **Unit Tests Passed**: `./gradlew testDebugUnitTest` runs cleanly.
- [ ] **Docs Linter Passed**: Verify all Markdown links are valid:
  ```bash
  python3 .agents/skills/project-docs/scripts/lint-docs.py .
  ```

---

## Related Documentation

* [Local Development Guide](local-development.md)
* [Testing Guide](testing.md)
* [Android Services Architecture](../services/android.md)
