<div align="center">

# 📱 Chege Photos

**Your personal, self-hosted photo gallery and backup solution.**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API_25+-green.svg?logo=android)](https://developer.android.com/)
[![Backend](https://img.shields.io/badge/Backend-CodeIgniter_4-EF4223.svg?logo=codeigniter)](https://codeigniter.com/)
[![Database](https://img.shields.io/badge/Database-MySQL-4479A1.svg?logo=mysql)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Supported-2496ED.svg?logo=docker)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Chege Photos is a robust, modern Android application built entirely with Kotlin. Designed with performance and clean architecture in mind, it seamlessly integrates with a containerized CodeIgniter 4 backend.

[🌍 Explore the Backend Repository](#) · [🐛 Report a Bug](#) · [✨ Request a Feature](#)

</div>

---

## 📖 About the Project

Chege Photos is a clean, modern gallery application that acts as a secure backup client. It organizes your local media and seamlessly syncs your favorite memories to your own private CodeIgniter 4 backend, ensuring you never lose a photo again.

### 🔗 Architecture & Integration
The mobile app relies on a decoupled architecture, consuming RESTful APIs served by a customized **CodeIgniter 4 (CI4)** backend. The backend infrastructure, including the **MySQL** database and **phpMyAdmin** interface, is fully containerized using **Docker** for easy local development, testing, and deployment.

**Key Highlights:**
* **MVVM Architecture:** Ensures separation of concerns, making the UI logic highly testable and maintainable.
* **Coroutines & Flow:** Handles asynchronous data streams seamlessly without blocking the main thread.
* **Robust Backend:** A lightweight, blazing-fast CI4 API providing secure data access.

---

## ✨ Features

* **Grid Layouts:** Beautiful, responsive photo grids using Material Design 3.
* **Cloud Sync:** Push photos to your personal CI4 backend securely.
* **Image Caching:** Fast loading and offline viewing using advanced image caching libraries.
* **Secure Storage:** Keeps your private photos out of big-tech clouds.
* **Dark Mode Support:** Fully responsive adaptive UI following Material Design 3 guidelines.

---

## 🛠 Tech Stack

### 📱 Android Client (Frontend)
| Technology | Description |
|---|---|
| **Kotlin** | Primary programming language |
| **MVVM** | Architectural pattern |
| **Retrofit + OkHttp** | Networking and API communication |
| **Coroutines & Flow** | Asynchronous programming |
| **Room / DataStore** | Local persistence and caching |

### ⚙️ Backend (API & Database)
| Technology | Description |
|---|---|
| **CodeIgniter 4** | PHP Framework serving the RESTful API |
| **MySQL** | Relational Database Management System |
| **Docker & Compose** | Containerization and environment orchestration |

---

## 📋 Prerequisites

Before you begin, ensure you have met the following requirements:
* **Android Studio:** Giraffe (or newer)
* **Java Development Kit (JDK):** Version 17+
* **Docker Desktop:** Installed and running (for backend services)
* **Git:** For version control

---

## 🚀 Installation & Setup

### 1. Clone the Repository
```bash
git clone https://github.com/YourOrg/Chege_Photos_App.git
cd Chege_Photos_App
```

### 2. Set Up the CodeIgniter 4 Backend (Docker)
The backend runs entirely in Docker containers. From the backend project directory:
```bash
# Build and start the containers in the background
docker-compose up -d --build

# Check if containers are running properly
docker-compose ps
```
* **API Endpoint:** The backend API will be available at `http://localhost:8080/api/` (adjust port as needed).
* **Database Management:** Access phpMyAdmin at `http://localhost:8081` using the credentials defined in your `docker-compose.yml`.

### 3. Configure the Android App
Open the Android project in Android Studio. You need to point the app to your local backend.
1. Open `local.properties` (or `gradle.properties` depending on your setup).
2. Add your local IP address for the API Base URL:
```properties
# Use 10.0.2.2 for the Android Emulator to connect to localhost
BASE_URL="http://10.0.2.2:8080/api/"

# Use your machine's physical IP address if testing on a physical device
# BASE_URL="http://192.168.1.xxx:8080/api/" 
```
3. Click **Sync Project with Gradle Files**.

### 4. Run the App
* **Using the Emulator:** Select your preferred AVD in Android Studio and hit **Run** (`Shift + F10`).
* **Using a Physical Device:** Connect your Android device via USB (ensure USB Debugging is enabled), select your device in the deployment target dropdown, and hit **Run**.

---

## ⚙️ Configuration

* **Build Variants:** The project utilizes Gradle build variants (`debug` and `release`). Ensure you select the `debug` variant for local development.
* **Environment Variables:** Production URLs and sensitive API keys should be injected securely via CI/CD pipelines and not hardcoded into the repository.

---

## 🤝 Contributing

We welcome contributions from the community! To contribute:

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the **MIT License**. See `LICENSE` for more information.

---

## 💬 Support

If you have any questions, encounter issues, or need further assistance with deployment, please reach out:

* **Email:** [info@chegecache.co.ke](mailto:info@chegecache.co.ke)
* **Website:** [chegecache.co.ke](https://chegecache.co.ke)
