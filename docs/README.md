# Chege Photos Android App — Engineering Handbook

Welcome to the engineering documentation for Chege Photos Android App. This documentation is intended for mobile engineers maintaining, debugging, or extending the Kotlin Jetpack Compose companion application.

If you only need to build and install the companion APK, see the [Root README](../README.md).

---

## Documentation Navigation

| I want to… | Go here |
|---|---|
| **Build & install APK without coding** | [../README.md](../README.md) |
| **Understand system architecture & C4 containers** | [architecture/overview.md](architecture/overview.md) |
| **Inspect network communication & Retrofit APIs** | [architecture/communication.md](architecture/communication.md) |
| **Review Room SQLite schemas & MediaStore indexing** | [architecture/data-and-storage.md](architecture/data-and-storage.md) |
| **Inspect threat model & mobile security hardening** | [architecture/threat-model.md](architecture/threat-model.md) |
| **Work on WorkManager background sync & Okio** | [services/android.md](services/android.md) |
| **Set up local Android Studio & SDK environment** | [engineering/local-development.md](engineering/local-development.md) |
| **Add a feature safely & Definition of Done** | [engineering/making-changes.md](engineering/making-changes.md) |
| **Execute unit and instrumented tests** | [engineering/testing.md](engineering/testing.md) |
| **Review CI/CD pipelines & automated tests** | [engineering/ci.md](engineering/ci.md) |
| **Review contributing guidelines & Compose rules** | [engineering/contributing.md](engineering/contributing.md) |
| **Troubleshoot Gradle & emulator networking** | [engineering/troubleshooting.md](engineering/troubleshooting.md) |
| **Review Keystore encryption & streaming uploads** | [engineering/security.md](engineering/security.md) |
| **Inspect ecosystem version matrix & release train** | [engineering/release.md](engineering/release.md) |
| **Review ADR 0001: Hybrid QR & Numeric Pairing** | [adr/0001-hybrid-qr-numeric-pairing.md](adr/0001-hybrid-qr-numeric-pairing.md) |
| **Review ADR 0003: On-Device OCR via ML Kit** | [adr/0003-on-device-ocr-via-ml-kit.md](adr/0003-on-device-ocr-via-ml-kit.md) |
| **Review operator configuration & sync settings** | [user/configuration.md](user/configuration.md) |
| **Troubleshoot runtime & connection issues** | [user/troubleshooting.md](user/troubleshooting.md) |

---

### Sibling Ecosystem Repositories

| Component | Responsibility | Repository URL | Documentation |
|---|---|---|---|
| **Web App** | CodeIgniter 4 Web UI, Auth & Sync Gateway | [GitHub Repo](https://github.com/niccher/Chege-Photos-WebApp) | [docs/](https://github.com/niccher/Chege-Photos-WebApp/tree/main/docs) |
| **ML Microservice** | FastAPI, InsightFace, YOLO, CLIP & Qdrant | [GitHub Repo](https://github.com/niccher/Chege-Photos-ML) | [docs/](https://github.com/niccher/Chege-Photos-ML/tree/main/docs) |
| **Android App** | Jetpack Compose Native Mobile Client | [GitHub Repo](https://github.com/niccher/Chege-Photos-Android) | [docs/](https://github.com/niccher/Chege-Photos-Android/tree/main/docs) |
