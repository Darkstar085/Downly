<div align="center">

<p align="center">
  <img src=".github/assets/logo.png" alt="Downly logo" width="25%">
</p>

Your personal media saver for Android.

<p>
  <a href="https://github.com/Darkstar085/Downly">
    <img src="https://img.shields.io/badge/Android-API%2030%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android API 30+">
  </a>
  <a href="https://developer.android.com/compose">
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
  </a>
  <a href="https://m3.material.io/">
    <img src="https://img.shields.io/badge/Design-Material%203-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material 3">
  </a>
  <a href="https://github.com/Darkstar085/Downly/releases">
    <img src="https://img.shields.io/github/v/release/Darkstar085/Downly?style=for-the-badge&label=Latest%20Release&color=FF6F00" alt="Latest release">
  </a>
  <a href="https://github.com/Darkstar085/Downly/releases">
    <img src="https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2FDarkstar085%2FDownly%2Fmain%2F.github%2Fbadges%2Fdownloads.json&style=for-the-badge" alt="APK downloads">
  </a>
  <a href="https://github.com/Darkstar085/Downly/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-E91E63?style=for-the-badge" alt="MIT License">
  </a>
</p>

</div>

---

## About

Downly is an Android media downloader built with Kotlin and Jetpack Compose. Paste a supported
media link, choose a video quality or audio option, and download media in the background with
progress and controls available from Android notifications.

## Screenshots

### Home & Media Selection

<p align="center">
  <img src=".github/assets/home.png" width="31%" alt="Home screen">
  <img src=".github/assets/media.png" width="31%" alt="Media details">
  <img src=".github/assets/media_video.png" width="31%" alt="Media video">
  <img src=".github/assets/media_audio.png" width="31%" alt="Media audio">
</p>

### Download History

<p align="center">
  <img src=".github/assets/history_empty.png" width="31%" alt="Empty history">
  <img src=".github/assets/history.png" width="31%" alt="Download history">
</p>

### Settings & About

<p align="center">
  <img src=".github/assets/settings.png" width="31%" alt="Settings">
  <img src=".github/assets/settings_about.png" width="31%" alt="About">
</p>

---

## Features

### Media Downloads

- Paste an HTTP/HTTPS media link or paste a URL from the clipboard.
- Fetch media title, uploader, duration, thumbnail, and available formats when provided by the
  source.
- Choose from available video quality options.
- Use **Best quality** to select the highest available video resolution or audio bitrate.
- Download audio using supported audio formats.
- Track queued, active, completed, and failed downloads in the app.
- Continue downloads in the background and monitor progress from Android notifications.
- Pause or cancel active downloads from notifications, and resume paused downloads in the app.
- Use format fallbacks when a preferred audio or video format is unavailable.
- See reported file sizes and quality-specific filenames for downloaded media.
- Get battery-optimization guidance to help background downloads run reliably.

### Download History

- Keep a local history of downloads.
- Filter download history by all items, videos, or audio.
- View media details such as file type, size, and download date.
- Open a completed file or play it with a compatible app.
- Detect duplicate downloads and choose whether to keep the existing file or replace it.
- Restore pending duplicate choices after app recreation and reconcile history when a file is replaced.
- Protect the existing file if replacement fails.
- Clear finished and failed records.

### Personalization

- System, Light, Dark, and Pure Black themes.
- Optional Android 12+ Dynamic Color using Material You wallpaper colors.
- Material 3 surfaces and responsive Compose layouts.

### Updates

- Check for new versions published through GitHub Releases.
- Download and validate the release manifest and APK digest before installation.
- Show update availability and download progress through the app's update flow.

---

## Downloads

Download builds from [**GitHub Releases**](https://github.com/Darkstar085/Downly/releases/latest).

Build the app locally using the instructions below.

---

## Design

Downly is built with **Jetpack Compose**, **AndroidX**, and **Material 3**.

The interface focuses on:

- Readable typography and a clear visual hierarchy.
- Rounded surfaces and theme-aware colors.
- Straightforward video and audio choices.
- A compact download history with media filters and clear file details.
- Android-native navigation, permissions, and update installation.

---

## Tech Stack

| Area                  | Technology                         |
|-----------------------|------------------------------------|
| Language              | Kotlin                             |
| UI                    | Jetpack Compose                    |
| Design                | Material 3                         |
| Build                 | Gradle Kotlin DSL                  |
| Android plugin        | Android Gradle Plugin              |
| Serialization         | Kotlinx Serialization              |
| Background work       | WorkManager                        |
| Animations            | Lottie Compose                     |
| Media download engine | YouTubeDL Kotlin Android artifacts |
| Java                  | Java 17                            |
| Minimum Android       | Android 11 / API 30                |
| Compile / Target      | Android API 37                     |

---

## Project Structure

```text
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── kotlin/com/sipun/downly/
    │   │   ├── core/update/
    │   │   ├── data/
    │   │   ├── presentation/
    │   │   │   ├── screens/
    │   │   │   └── theme/
    │   │   └── utils/
    │   └── res/
    │       ├── drawable/
    │       ├── mipmap-*/
    │       ├── raw/
    │       ├── values/
    │       └── xml/
    ├── test/
    └── androidTest/
```

- **UI:** Download, History, and Settings screens are implemented with Jetpack Compose.
- **Update flow:** GitHub Release lookup, manifest validation, APK digest verification, and installer
  handling live under `core/update/`.
- **Local data:** Download state and history are managed in the app; downloaded media stays in
  app-specific/media storage.

---

## Building

### Requirements

- Android Studio and Android SDK 37.
- JDK 17.
- Android 11 or later (API 30+).
- The Downly YouTubeDL Kotlin artifacts must be available in the local Maven repository before
  Gradle can resolve them.

### Prepare dependencies

The app currently resolves these artifacts from `mavenLocal()`, not Maven Central:

- `io.github.darkstar085.youtubedl-kotlin:library:1.0.1`
- `io.github.darkstar085.youtubedl-kotlin:ffmpeg:1.0.1`
- `io.github.darkstar085.youtubedl-kotlin:aria2c:1.0.1`

Publish the corresponding `library`, `ffmpeg`, and `aria2c` modules from the matching
`youtubedl-kotlin` source project to your local Maven repository before building. A fresh clone does
not include these Maven artifacts. CI builds need the same artifacts installed or made available
from a remote Maven repository.

### Build a debug APK

Linux / macOS:

```bash
./gradlew :app:assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

---

## Storage, Permissions & Privacy

Downloads are stored in app-specific/media storage. No broad storage permission is requested by this
app. Clearing app data or uninstalling removes app-specific files.

Downly uses network access to retrieve media, check supported sources, and query GitHub Releases for
updates. Source-site behavior and available formats can change. Some media may require
authentication or may be unavailable.

---

## License

Downly is licensed under the [MIT License](LICENSE).

---

<div align="center">

**Downly — Your personal media saver.**

Made with ❤️ by Sipun.

</div>
