# ADR 0003: On-Device OCR via Google ML Kit

* **Status**: Accepted
* **Date**: 2026-09-06
* **Deciders**: Systems & Mobile Team

---

## Context & Problem Statement

Users search their local photo library for text embedded in receipts, documents, signs, and screenshots. Transmitting full-resolution image bitmaps to the backend server solely for optical character recognition (OCR) causes high network egress, battery drain, and server compute overhead.

## Decision Drivers

1. **Instant Offline Search**: Users must be able to search photos by text without an active server connection.
2. **Network Bandwidth Economy**: Conserve mobile data and avoid saturating home Wi-Fi with duplicate image transmissions.
3. **Battery & Device Performance**: Leverage mobile hardware acceleration (NPU/GPU) via lightweight on-device models.

## Considered Options

1. **Server-Side Tesseract OCR**: Upload all images to the server and process asynchronously in PHP or Python.
2. **On-Device OCR via Google ML Kit**: Run the on-device Text Recognition API in background WorkManager jobs and cache recognized tokens in Room SQLite.

## Decision Outcome

Chosen option: **Option 2 (On-Device OCR via Google ML Kit)**.

### Implementation Details:
* When indexing local media, `OcrIndexingWorker` passes local `Uri` bitmaps to `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`.
* Extracted text blocks and keywords are stored in the local Room database (`photo_text_index` FTS table).
* When a user searches in the app, Room executes instant Full-Text Search (FTS) queries locally in < 15 ms.

## Consequences

### Positive
* Instant text search operates completely offline.
* Zero additional compute load placed on the WebApp or ML microservice.
* Zero network egress for OCR indexing.

### Negative
* Adds ML Kit Text Recognition dependency to the Android client (~5 MB model download).
