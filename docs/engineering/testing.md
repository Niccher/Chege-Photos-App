# Testing Guide — Chege Photos Android

This document outlines the testing strategy for Chege Photos Android, including unit tests, in-memory Room database tests, and instrumented UI tests.

---

## Testing Frameworks

* **Unit Testing**: JUnit 4, Kotlinx Coroutines Test, MockK
* **Database Testing**: Room In-Memory Database Builder
* **Network Mocking**: OkHttp `MockWebServer`
* **Instrumented Testing**: AndroidX Test Runner, Espresso, Compose UI Test

---

## 1. Local JVM Unit Tests

Unit tests execute locally on the development machine without needing an Android device or emulator.

### Run All Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Writing a Room In-Memory Test
When verifying database operations or migration logic, instantiate an ephemeral database:

```kotlin
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.niccher.chege_photos_app.data.PhotoDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test

class PhotoDaoTest {
    private lateinit var db: PhotoDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, PhotoDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrievePhoto() = kotlinx.coroutines.runBlocking {
        val sample = com.niccher.chege_photos_app.models.CachedPhoto(
            id = "1",
            filename = "sample.jpg",
            path = "uploads/sample.jpg",
            thumbnail_path = null,
            size = "1024",
            taken_at = "2026-01-01 12:00:00",
            width = 1920,
            height = 1080,
            latitude = null,
            longitude = null,
            exif_data = null,
            mime_type = "image/jpeg"
        )
        db.photoDao().insertPhotos(listOf(sample))
        val retrieved = db.photoDao().getPhotoById("1")
        assert(retrieved?.filename == "sample.jpg")
    }
}
```

---

## 2. Instrumented Android Tests

Instrumented tests run on a connected Android device or emulator to test hardware and framework APIs:

```bash
./gradlew connectedDebugAndroidTest
```

---

## 3. Code Quality & Linting

Run Android Lint to identify layout issues, performance bottlenecks, and deprecated API usages:

```bash
./gradlew lint
```

HTML and XML reports are generated in `app/build/reports/lint-results-debug.html`.

---

## 4. Documentation Verification

Verify documentation links and formatting:

```bash
python3 .agents/skills/project-docs/scripts/lint-docs.py .
```

---

## Related Documentation

* [Local Development Guide](local-development.md)
* [Making Changes & Definition of Done](making-changes.md)
* [Android Services Architecture](../services/android.md)
