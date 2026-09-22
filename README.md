# Vippatti Sarana

[![Build Android APK](https://github.com/Karunakar1326/vippatti_sarana/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Karunakar1326/vippatti_sarana/actions/workflows/build-apk.yml)
[![Direct APK Download](https://img.shields.io/badge/Download-APK-brightgreen?logo=android)](https://nightly.link/Karunakar1326/vippatti_sarana/workflows/build-apk/main/Vippatti-Sarana-APK.zip)

> **Disaster Intelligence & Emergency Response Platform for Vulnerable Communities**

Vippatti Sarana is an Android-based disaster management application designed to help users assess disaster risks, identify safer areas, access evacuation routes, and receive relevant disaster information.

Vippatti Sarana is an **all-India** disaster-management and relocation decision-support application: live data sources are queried across India, and the user's own location drives every assessment. **Idukki, Kerala** appears only as a configurable pilot/demo region (sample shelter network and demonstration records), not as a limitation of scope.

---

## 🚨 Features

- 🗺️ **Disaster Radar** — View hazards, disaster events, safe zones, and evacuation routes.
- 📍 **Risk Assessment** — Assess disaster risk based on the user's location.
- 🏠 **Safe Zone Detection** — Identify and rank nearby safer locations.
- 🛣️ **Evacuation Routing** — Plan evacuation routes with alternative routes.
- 🌋 **Disaster Intelligence** — Uses data from USGS, NASA FIRMS, IMD CAP, and user reports.
- 📰 **Disaster News** — Fetch relevant disaster-related news through GNews.
- 📢 **Emergency Tools** — SOS, emergency contacts, flashlight, siren, and battery information.
- 🔊 **Audio Bulletin** — Provides spoken updates about risk, recommended actions, and relevant news.
- 📝 **Incident Reporting** — Report incidents and provide situation information.
- 📦 **Offline Support** — Caches selected data for use during limited connectivity.

---

## 🛠️ Tech Stack

| Category         | Technology                           |
| ---------------- | ------------------------------------ |
| Platform         | Android                              |
| Language         | Kotlin                               |
| UI               | Jetpack Compose                      |
| Architecture     | MVVM                                 |
| Maps             | OSMDroid + OpenStreetMap             |
| Routing          | OSRM                                 |
| Networking       | OkHttp                               |
| Local Storage    | File Cache                           |
| Backend Services | Firebase                             |
| News             | GNews API                            |
| Disaster Data    | USGS, NASA FIRMS, IMD CAP            |
| Testing          | JUnit, Robolectric, Compose UI Tests |

---

## 🏗️ Architecture

```text
                    Vippatti Sarana
                          │
                          ▼
                   Jetpack Compose
                          │
                          ▼
                  VippattiViewModel
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    Risk Assessment   Safe Zones      Routing
          │               │               │
          └───────────────┼───────────────┘
                          ▼
                 Emergency Response
```

For the detailed architecture and data flow, see [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## 📂 Project Structure

```text
app/
├── src/main/java/
│   └── com/example/
│       ├── data/
│       │   ├── risk/
│       │   ├── shelters/
│       │   ├── routing/
│       │   ├── reports/
│       │   ├── news/
│       │   └── disaster/
│       │
│       └── ui/
│           └── components/
│
├── build.gradle.kts
└── .env.example

ARCHITECTURE.md
README.md
```

---

## 📥 Download & Install the App

To install **Vippatti Sarana** on your Android device:

### Option 1: Direct Public Download Link (Instant Download)
Click the direct public download link below to get the latest built APK without needing a GitHub account:
👉 **[Download Vippatti-Sarana-debug.apk](https://nightly.link/Karunakar1326/vippatti_sarana/workflows/build-apk/main/Vippatti-Sarana-APK.zip)**

### Option 2: GitHub Releases & Artifacts
1. Go to the **[Releases](../../releases)** section of this repository.
2. Open the **latest release** to download `Vippatti-Sarana-debug.apk`.
3. Alternatively, check the latest run under the **[Actions](../../actions/workflows/build-apk.yml)** tab and download the **Vippatti-Sarana-APK** artifact.

### Installation Instructions
1. Download the APK using one of the links above.
2. On your Android device, enable **"Install from unknown sources"** (if prompted) for your browser or file manager.
3. Open the downloaded APK file and follow the on-screen prompts to install the app.

---

## ⚙️ Setup (For Developers)

### Requirements

- Android Studio
- JDK 11+
- Android SDK 36
- Android device or emulator

### Environment Variables

Create an `app/.env` file and add the required API keys:

```env
GNEWS_API_KEY=YOUR_GNEWS_API_KEY
FIRMS_MAP_KEY=YOUR_FIRMS_MAP_KEY
```

> **Note:** Do not commit `.env` or API keys to the repository.

### Build

```bash
./gradlew.bat compileDebugKotlin
```

### Run Tests

```bash
./gradlew.bat testDebugUnitTest
```

### Build APK

```bash
./gradlew.bat assembleDebug
```

---

## 🔐 Data Transparency

Vippatti Sarana is designed to distinguish between real data and fallback/demo data.

- Device GPS data is clearly identified when available.
- Fallback location data is explicitly labelled.
- Disaster events retain their respective data sources.
- News is clearly marked as **not an official emergency alert**.
- Historical EM-DAT context is attributed, versioned, and kept separate from live decisions — see [EMDAT_ATTRIBUTION.md](./EMDAT_ATTRIBUTION.md).
- The application avoids fabricating alerts, routes, or verification statuses.

---

## 🚧 Project Status

Vippatti Sarana is an active disaster-management pilot project focused on **risk awareness**, **safe-zone identification**, **evacuation planning**, and **emergency assistance**.

---

## ⚠️ Disclaimer

Vippatti Sarana is a software prototype/pilot and **should not be considered a replacement for official emergency warnings, government advisories, or instructions from emergency authorities**.

---

**Vippatti Sarana — Technology for Safer Communities.**
