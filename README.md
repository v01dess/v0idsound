<div align="center">

# `v0idsound`

### A lightweight local music player for Android

**Fast indexing. Local files. No account. No streaming required.**

[![Android](https://img.shields.io/badge/Android-24%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Media3](https://img.shields.io/badge/AndroidX-Media3-4285F4?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/media)
[![License](https://img.shields.io/badge/License-MIT-FF1744?style=for-the-badge)](LICENSE)

</div>

# ! WARNING !
If you do not have `Install Unknown Apps` you might not be able to download/install the .apk file, therefor not being able to use the application.

---

> **v0idsound** is a local-first Android music player built for music already on your phone.  
> It indexes your library, plays local audio, supports custom folders, and lets you make the app look like yours.

## Screenshots

# --- PLACEHOLDER ---
# as you can tell there's
# like nothing here yet
# just enjoy the app gng

## Features

- **Local-first playback** — play music stored on the device
- **Fast library search** — search indexed tracks by title, artist, or album
- **Album artwork** — displays MediaStore album art where available
- **Now-playing popout** — compact player panel with seek, previous, next, repeat, and random track controls
- **Random track roll** — pick a surprise song from the current library
- **Custom folders** — include audio from folders you choose
- **MediaStore support** — include Android-indexed music automatically
- **Flexible indexing** — duration threshold, music-only mode, broad scan, recording filtering, folder-depth control
- **Themes** — dark mode plus preset and custom accent colors
- **Custom color picker** — quick color presets, hex input, and visual HSV picker
- **Long-title marquee** — optionally scroll long track titles in the player
- **Offline by design** — no account, no server, no music-streaming dependency

## Installation

### Install from APK

1. Download the latest `v0idsound.apk` (sometines `app-release.apk`).
2. Open the downloaded file on your Android device.
3. Allow your browser or file manager to install unknown apps if Android asks.
4. Ignore the warnings, if you're unsure, visit https://virustotal.com
4. Install **v0idsound**.
5. Open the app and grant music/audio permission.
6. Your local library will be indexed and displayed.

> v0idsound requires Android 7.0 or newer.

## Usage

### Play music

1. Open the **Library** screen.
2. Tap any track to play it.
3. Tap the floating play button in the bottom-right corner.
4. Use the currently-playing panel to seek, skip tracks, repeat, or roll a random song.

### Search your library

Use the search field at the top of the library. Search matches:

- Track title
- Artist
- Album

Searching filters the already-indexed local list, so it does not rescan storage every time you type.

### Customize the app

Open **Settings → Customization** to:

- Enable or disable dark theme
- Pick a quick accent color
- Enter a custom hex color
- Use the visual color picker
- Enable or disable scrolling long track titles

### Index custom folders

Open **Settings → Indexing** to:

1. Tap **Add folder to scan**
2. Select a directory using Android’s folder picker
3. Adjust the maximum folder depth if needed
4. Tap **Rescan library now**

## Built With

| Technology | Purpose |
|---|---|
| [Kotlin](https://kotlinlang.org/) | Application language |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | User interface |
| [AndroidX Media3](https://developer.android.com/media) | Audio playback and media session |
| [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) | Persistent settings |
| [Coil](https://coil-kt.github.io/coil/compose/) | Album-art image loading |
| [MediaStore](https://developer.android.com/reference/android/provider/MediaStore) | Android media-library discovery |
| [DocumentFile](https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile) | Custom folder scanning |

## Roadmap

- [x] Local MediaStore library indexing
- [x] Custom folder scanning
- [x] Search by title, artist, and album
- [x] Album-art support
- [x] Now-playing popup panel
- [x] Dark theme and custom accent colors
- [x] Visual color picker
- [x] Long-title scrolling option
- [ ] Library filters
- [ ] Queues and playlists
- [ ] Favorites
- [ ] Album and artist browsing
- [ ] Better metadata editing
- [ ] Equalizer support
- [ ] Widget and lock-screen improvements

## Development

Clone the project: (not sure either lmfao)

```bash
git clone [https://github.com/v01dess/v0idsound.git](https://github.com/v0idess/v0idsound.git)
cd v0idsound
```

Open the project in Android Studio, let Gradle sync, then run it on an Android device or emulator.

### Requirements

- Android Studio
- JDK 11
- Android SDK 37
- Android device or emulator running Android 7.0+
- THESE are not certain, i am uh.. yeah!

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

<div align="center">

Made for local music. Light and Right.

</div>
