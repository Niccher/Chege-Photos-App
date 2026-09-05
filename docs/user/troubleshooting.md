# Troubleshooting Guide — Chege Photos Android

This guide addresses common errors, permission issues, network failures, and synchronization delays encountered when using the Chege Photos Android application.

---

## Common Issues & Solutions

### 1. `CLEARTEXT communication to ... not permitted by network security policy`

* **Symptom**: Connecting to a local IP address (e.g. `http://192.168.1.100:8080`) fails with a network security error.
* **Root Cause**: Android 9 (API 28) and newer block unencrypted HTTP traffic by default.
* **Resolution**:
  * For local testing, ensure the debug build manifest includes `android:usesCleartextTraffic="true"` or configure an explicit domain exception in `res/xml/network_security_config.xml`.
  * For production, always serve the WebApp over a trusted HTTPS domain.

---

### 2. Camera Opens to a Black Screen During QR Scan

* **Symptom**: Tapping "Scan QR Code" yields a blank screen or immediately closes.
* **Root Cause**: Android camera permission was denied or revoked.
* **Resolution**:
  1. Open Android **Settings → Apps → Chege Photos → Permissions**.
  2. Select **Camera** and set to **Allow only while using the app**.
  3. Re-launch the application and attempt pairing again.

---

### 3. Background Sync Stops Working / Photos Not Uploading Automatically

* **Symptom**: Photos taken on the device are not synced to the cloud until the app is opened manually.
* **Root Cause**: Android battery optimization (Doze mode) or manufacturer aggressive task killers are throttling `WorkManager` background workers.
* **Resolution**:
  1. Open Android **Settings → Apps → Chege Photos → Battery**.
  2. Change battery usage from **Optimized** to **Unrestricted**.
  3. Verify in app settings that **Wi-Fi Only** is disabled if currently running on mobile cellular data.
  4. Ensure the device has sufficient storage space (>500 MB).

---

### 4. Permission Denied on Android 13+ (API 33+)

* **Symptom**: App crashes or shows empty gallery after fresh installation on Android 13 or 14.
* **Root Cause**: Android 13 replaced `READ_EXTERNAL_STORAGE` with granular permissions (`READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO`).
* **Resolution**:
  * Ensure the runtime permission prompt grants access to photos and videos.
  * In **Settings → Apps → Chege Photos → Permissions**, grant access to **Photos and videos**.

---

### 5. `SSLHandshakeException: CertPathValidatorException`

* **Symptom**: App cannot connect to server; logs display self-signed or invalid SSL certificate errors.
* **Root Cause**: The WebApp HTTPS endpoint uses a self-signed certificate that is not recognized by the Android system trust store.
* **Resolution**:
  * Use Let's Encrypt or a CA-signed TLS certificate on your server reverse proxy (Nginx / Caddy / Cloudflare).
  * For local self-signed testing, add the custom root certificate to your Android device's User CA credentials in Android Settings.

---

## Diagnostic Log Extraction

To capture real-time application logs using ADB:

```bash
# Filter Chege Photos logs
adb logcat -s "ChegePhotos:*" "WorkManager:*" "OkHttp:*"
```

---

## Related Documentation

* [Setup & Run Guide](setup-and-run.md)
* [Configuration Reference](configuration.md)
* [Android Architecture Overview](../architecture/overview.md)
