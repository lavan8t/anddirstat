[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android Studio](https://img.shields.io/badge/Android_Studio-3DDC84?style=flat&logo=androidstudio&logoColor=white)](https://developer.android.com/studio)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

<img src="logo.svg" alt="Project Logo" width="120">


Fast, interactive disk usage analyzer and storage treemap for Android.

[![Latest Release](https://img.shields.io/github/v/release/lavan8t/anddirstat?style=flat&color=34A853&logoColor=white&label=Release)](https://github.com/lavan8t/anddirstat/releases/latest)
[![Total Downloads](https://img.shields.io/github/downloads/lavan8t/anddirstat/total?style=flat&logo=github&color=34A853&logoColor=white&label=Downloads)](https://github.com/lavan8t/anddirstat/releases)
[![Stars](https://img.shields.io/github/stars/lavan8t/anddirstat?style=flat&logo=github&color=34A853&logoColor=white&label=Stars)](https://github.com/lavan8t/anddirstat/stargazers)

[![Report Bug](https://img.shields.io/badge/Report_Bug-34A853?style=flat&logo=github&logoColor=white)](https://github.com/lavan8t/anddirstat/issues/new?labels=bug)
[![Request Feature](https://img.shields.io/badge/Request_Feature-34A853?style=flat&logo=github&logoColor=white)](https://github.com/lavan8t/anddirstat/issues/new?labels=enhancement)

---

## The idea

I'd been using [WinDirStat](https://windirstat.net) on Windows for years. It draws your entire drive as a rectangle, where each file is a colored block sized to its actual bytes. You can see at a glance that one folder is eating half your disk. Nothing else comes close to that kind of clarity.

Android never had anything like it. The built-in storage settings show you a pie chart with labels like "Other files" and call it a day. I wanted the real thing on my phone, so I built it.

---

## What it does

The main screen is a treemap. Every file and folder on your device gets drawn as a rectangle. Larger files are larger rectangles. Folders are subdivided into their contents. Colors are assigned by file type so images, videos, APKs, and documents are immediately distinguishable.

Tap any block to drill into that folder. The treemap redraws for that subtree. Tap back to go up. Long-press to select and delete directly from the map.

Beyond the treemap there's a full file explorer backed by the same scan, a file types breakdown with per-extension sizes, and a Discover screen where you can search by name or size threshold and use presets like "files over 1 GB" or "old downloads".

Cleaners for screenshots, duplicates, empty folders, and a recycle bin with restore are all built in.

---

## How the treemap works

The scanner walks the filesystem once and builds a tree of `CompactNode` objects. Each node holds a name, a size in bytes, and an array of children. Directories accumulate size bottom-up as the walk finishes. The whole tree for a typical phone with 50,000 files fits in a few MB of heap.

The treemap layout uses a squarified algorithm. Given a rectangle and a list of children sorted by size, it packs them into rows where each rectangle is as close to square as possible. Pure horizontal or vertical strips look bad and make small files invisible. Squarification keeps aspect ratios reasonable across several orders of magnitude of file size.

The layout is computed once per canvas size on a background thread and cached. Recompositions from taps and selection changes read from that cache without recalculating. After a deletion the node is pruned from the in-memory tree and the layout recalculates, so the scan only runs once per session.

Colors come from a fixed palette keyed by extension category. Images are orange, videos are red, audio is purple, APKs are green, documents are blue, everything else is grey. The shade shifts slightly per node so adjacent same-type files don't merge visually.

---

## Features

- Treemap with drill-down navigation and multi-select delete
- Full file explorer with the same scan backing it
- File types breakdown by extension
- Discover: search by name, extension, or size (`> 500MB`, `< 1KB`)
- Presets: starred files, files over 1 GB, old downloads, APKs
- Recycle bin with restore
- Screenshot cleaner
- Duplicate file finder
- Empty folder remover
- Material 3 Expressive UI, no ads, no tracking, no network calls

---

## Download

Download the APK for your device architecture from the [latest release](https://github.com/lavan8t/anddirstat/releases/latest):

| APK | Architecture | Compatibility |
|---|---|---|
| [**app-arm64-v8a-release.apk**](https://github.com/lavan8t/anddirstat/releases/latest/download/app-arm64-v8a-release.apk) | `arm64-v8a` | Most modern phones (64-bit ARM) |
| [**app-universal-release.apk**](https://github.com/lavan8t/anddirstat/releases/latest/download/app-universal-release.apk) | `universal` | Works everywhere (recommended if unsure) |
| [**app-armeabi-v7a-release.apk**](https://github.com/lavan8t/anddirstat/releases/latest/download/app-armeabi-v7a-release.apk) | `armeabi-v7a` | Older 32-bit ARM devices |
| [**app-x86_64-release.apk**](https://github.com/lavan8t/anddirstat/releases/latest/download/app-x86_64-release.apk) | `x86_64` | Android emulators & ChromeOS |

If unsure, pick [**app-universal-release.apk**](https://github.com/lavan8t/anddirstat/releases/latest/download/app-universal-release.apk).

---

## Building

```bash
git clone https://github.com/lavan8t/anddirstat.git
cd anddirstat
./gradlew assembleRelease
```

APKs land in `app/build/outputs/apk/release/`.

---

## Contributing

Pull requests are welcome. Open an issue first if you are planning something large.

Things that would be useful:
- More file type colors and categories
- Better handling of `Android/data` on different OEM builds
- Tablet layout
- Translations

Fork, branch off `main`, open a PR.

---

## Credit

Inspired by [WinDirStat](https://windirstat.net), the original disk usage visualizer for Windows. The squarified treemap algorithm traces back to research by Bruls, Huizing, and van Wijk at TU/e.
