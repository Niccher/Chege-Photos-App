# 📸 Open Photo Sync

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Niccher/My-Photos-App)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Linux-lightgrey.svg)](#)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)](#)

A professional, self-hosted photo management ecosystem. **Open Photo Sync** bridges the gap between privacy and convenience, providing a powerful Android client and a lightweight, containerized backend. No middleman, no subscription fees—just your memories, secured by you.

---

## ✨ Features

Open Photo Sync is packed with features designed for power users who value their privacy.

*   🛡️ **Privacy First (Self-Hosted)**: Complete sovereignty over your data. Connect the app to your private API and never worry about third-party data mining again.
*   🔒 **Biometric Security**: Keep your memories private with integrated Fingerprint and Face Unlock protection, ensuring only you can access your gallery.
*   🔄 **Intelligent Synchronization**: A robust background engine handles batch uploads with retry logic and real-time status updates.
*   🖼️ **Immersive Browsing**: Experience your photos in a beautiful, high-performance `LazyVerticalGrid` with an edge-to-edge, swipeable fullscreen carousel.
*   📁 **Smart Management**: Effortlessly organize your collection with dedicated views for **Favorites**, **Archive**, **Memories**, and a recoverable **Trash** system.
*   🌍 **Explore Mode**: Rediscover your travels through a map-based interface that clusters photos by their capture location metadata.
*   📤 **Deep System Integration**: Share photos or videos directly from your phone's native gallery or other apps to initiate an instant sync.
*   📥 **Cloud Download**: Restoration is just a tap away. Download any remote photo back to your device's local storage with a single click.

---

## 🚀 Installation & Setup

### 1. Backend (Server)
The backend is built with **CodeIgniter 4** and is fully containerized for effortless deployment.

1.  **Clone & Environment**:
    ```bash
    git clone https://github.com/Niccher/My-Photos-App.git
    cd backend
    cp .env.example .env
    ```
2.  **Docker Deployment**:
    ```bash
    docker-compose up -d --build
    ```
3.  **Initialize Database**:
    ```bash
    docker-compose exec app php spark migrate
    ```

### 2. Android Application
1.  Open the `app/` directory in **Android Studio**.
2.  Sync Gradle dependencies (requires SDK 33+).
3.  Build and run on your physical device.
4.  **Connection**: At the login screen, enter your server's URL (e.g., `https://photos.yourdomain.com/`).

---

## 📱 Usage

1.  **Login**: Authenticate with your server credentials.
2.  **Sync**: Tap the "Sync Now" button on the Sync screen to upload new local photos.
3.  **Browse**: Use the Gallery tab to view local photos or the sidebar to explore remote collections.
4.  **Lock**: Ensure Biometrics are enabled in your device settings for automatic app locking.

---

## ⚙️ Configuration

The system is highly configurable via environment variables in the backend:

| Variable | Description | Default |
| :--- | :--- | :--- |
| `DB_NAME` | MySQL Database Name | `photos` |
| `UPLOAD_LIMIT` | Maximum file size for uploads | `50MB` |
| `CI_ENVIRONMENT` | Backend environment mode | `production` |

---

## 🛠 Technologies Used

| Layer | Stack | Badges |
| :--- | :--- | :--- |
| **Android** | Kotlin, Jetpack Compose | ![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white) ![Compose](https://img.shields.io/badge/Compose-4285F4?style=flat&logo=android&logoColor=white) |
| **Backend** | PHP 8.2, CodeIgniter 4 | ![PHP](https://img.shields.io/badge/PHP-777BB4?style=flat&logo=php&logoColor=white) ![CI4](https://img.shields.io/badge/CI4-EF4223?style=flat&logo=codeigniter&logoColor=white) |
| **Database** | MySQL 8.0 | ![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat&logo=mysql&logoColor=white) |
| **Infra** | Docker, Nginx | ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white) |

---

## 🤝 Contributing

Contributions make the open-source community thrive. 
1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your Changes (`git commit -m 'Add AmazingFeature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

<p align="center">Made with ❤️ for Privacy Enthusiasts</p>

 