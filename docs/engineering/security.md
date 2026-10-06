# Engineering Security Architecture

Implementation details of Android Keystore cryptographic management, zero-copy streaming upload security, and biometric gating in Chege Photos Android App.

---

## 1. Credential Security & Keystore Integration

Personal Access Tokens and server URLs are protected using Android Jetpack Security:

* **MasterKey**: Generated in the hardware-backed `AndroidKeyStore` using AES-256-GCM.
* **EncryptedSharedPreferences**: Encrypts keys and values transparently before writing to disk.
* **Plaintext Leak Prevention**: No raw tokens are ever logged via `Logcat` or included in crash reporting bundles.

---

## 2. Zero-Copy Streaming Upload Pipeline (`Okio`)

To prevent Out-Of-Memory crashes when uploading large 4K video files, the app implements a streaming `RequestBody`:

```kotlin
// Okio-backed zero-copy streaming RequestBody
class StreamingFileRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val contentType: MediaType?,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long {
        return contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    }

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val source = inputStream.source()
            val buffer = Buffer()
            var uploaded = 0L
            var read: Long

            while (source.read(buffer, 65536L).also { read = it } != -1L) {
                sink.write(buffer, read)
                uploaded += read
                onProgress(uploaded, total)
            }
        }
    }
}
```

---

## 3. Biometric Vault Gating

The Private Vault view is secured using the AndroidX Biometric library:
* Authenticates against device biometrics (`BIOMETRIC_STRONG`) or device credential PIN/Pattern.
* Successfully authenticated cipher instances decrypt the local vault cache in memory only.
