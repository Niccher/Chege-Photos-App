# Configuration & Server Pairing — Chege Photos Android

This guide explains how to pair the Android app with your Chege Photos WebApp server and configure background synchronization constraints.

---

## 1. Pairing with Your Server

Before the app can sync photos or load cloud albums, it must pair with your WebApp instance.

### Option A: Frictionless QR Code Pairing (Recommended)
1. Log into your **Chege Photos WebApp** in a desktop or laptop browser.
2. Navigate to **User Profile / Settings → Mobile Companion App**.
3. Click **Generate Pairing Token**. An 8-character token encoded into a QR code will appear on screen.
4. On your Android phone, launch the Chege Photos app.
5. Tap **Scan QR Code to Connect**.
6. Grant camera permission and point the camera at your desktop screen.
7. The app instantly retrieves the server URL, performs a cryptographic handshake with the device fingerprint, and securely stores the session token.

### Option B: Manual Server URL Entry
If camera access is unavailable or connecting to a custom local network IP:
1. In the app login screen, tap **Enter Server URL Manually**.
2. Input the complete WebApp URL:
   * **Local LAN**: `http://192.168.1.50:8080`
   * **Production Domain**: `https://photos.example.com`
3. Enter your account username and password or paste an API token.
4. Tap **Connect**.

---

## 2. Background Sync Settings

Access sync settings in the app by tapping **Settings → Background Backup**:

| Setting | Default | Options | Description |
|---|---|---|---|
| **Auto Backup** | `Enabled` | `Enabled` / `Disabled` | Toggles automatic background upload via WorkManager. |
| **Wi-Fi Only** | `Enabled` | `Enabled` / `Disabled` | Restricts background media uploads to active Wi-Fi connections to avoid cellular data charges. |
| **Require Charging** | `Disabled` | `Enabled` / `Disabled` | Defer heavy background sync until device is plugged into a power source. |
| **Sync Interval** | `1 hour` | `15 min`, `1 hr`, `6 hrs`, `24 hrs` | Frequency of periodic media checks. |
| **Included Folders** | `DCIM/Camera` | Multi-select directories | Specific on-device media folders targeted for automatic cloud backup. |

---

## Related Documentation

* [Setup & Run Guide](setup-and-run.md)
* [Troubleshooting Guide](troubleshooting.md)
* [WorkManager Background Architecture](../services/android.md)
