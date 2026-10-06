# ADR 0001: Hybrid QR & Numeric Code Mobile Pairing

* **Status**: Accepted
* **Date**: 2026-09-06
* **Deciders**: Systems & Mobile Team

---

## Context & Problem Statement

Connecting a mobile phone to a self-hosted server running on a local network usually requires manually entering complex IP addresses (e.g. `http://192.168.1.150`) followed by authentication credentials. This results in high friction and typing mistakes.

## Decision Drivers

1. **Effortless Pairing**: Seamless connection without typing long URLs.
2. **Camera Permissiveness**: Provide a robust fallback if camera access is denied or hardware fails.
3. **Security**: Avoid passing raw user passwords over unencrypted local networks.

## Considered Options

1. **Manual URL + Credentials Entry**: User types server URL, email, and password.
2. **QR Code Scanning Only**: Camera scans encoded JSON payload containing connection string and token.
3. **Hybrid QR + 6-Digit Numeric Fallback**: QR scan with manual server URL and 6-digit PIN input dialog.

## Decision Outcome

Chosen option: **Option 3 (Hybrid QR + 6-Digit Numeric Fallback)**.

### Implementation Details:
* Mobile companion app opens with a choice: **Scan QR Code** or **Enter Details Manually**.
* Scanning uses Google ML Kit Barcode Scanning API for sub-second recognition of the desktop QR code.
* If camera permissions are denied, user can enter the server LAN IP and the 6-digit numeric PIN shown on the desktop browser.
* Mobile client exchanges this code for a long-lived `Personal Access Token` bound to the device's hardware identifier.

## Consequences

### Positive
* Instant connection for 95% of users via camera scanning.
* Reliable fallback for users with broken cameras or permission restrictions.

### Negative
* Requires bundling ML Kit Barcode scanning dependencies in the APK (~2.5 MB).
