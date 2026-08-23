# AndDirStat

Android storage analyzer built with Material 3 Expressive. It scans your device, draws an interactive treemap of every file and folder sized to actual bytes, then gives you tools to clean up what you find.

Most storage apps show you pie charts with vague categories. AndDirStat shows you the actual files taking up space, organized visually so large things are immediately obvious. Tap any block in the treemap to drill into that folder. Navigate from there to delete, star, or share directly.

[![Download latest](https://img.shields.io/github/v/release/lavan8t/AndDirStat?label=Download&style=for-the-badge)](https://github.com/lavan8t/AndDirStat/releases/latest)

Requires Android 10 (API 29) or higher.

---

## Features

**Treemap.** Every file gets a rectangle sized to its bytes, colored by type. Large things are large on screen. Tap to drill in, tap again to go back up.

**Explorer.** A full folder browser backed by the same scan. Navigation is instant because the tree is already in memory.

**File types.** Total size per extension with bar charts. Find out you have 4 GB of forgotten `.mov` files.

**Discover.** Search by name, extension, or size threshold (`> 500MB`, `< 100KB`). Presets for starred files, files over 1 GB, old downloads, and APKs. Large apps get their own list.

**Cleaners.**
- Recycle bin with restore
- Screenshot cleaner  
- Duplicate file finder
- Empty folder remover
- Starred files

**External storage.** Connect a USB drive or SD card and a chip appears below the app bar showing the device name. Tap it to list all connected volumes and map any of them independently.

---

## Download

Always points to the latest release:

[![Download](https://img.shields.io/github/v/release/lavan8t/AndDirStat?label=Latest%20Release&style=for-the-badge&logo=android)](https://github.com/lavan8t/AndDirStat/releases/latest)

Four APK variants are attached to each release:

| APK | Devices |
|---|---|
| `arm64-v8a` | Most phones made after 2016 |
| `armeabi-v7a` | Older 32-bit ARM devices |
| `x86_64` | Emulators |
| `universal` | Works everywhere |

If you are unsure, pick `universal`.

---

## Building

```bash
git clone https://github.com/lavan8t/AndDirStat.git
cd AndDirStat
./gradlew assembleRelease
```

APKs land in `app/build/outputs/apk/release/`.

---

## Contributing

Pull requests are welcome. Open an issue first if you are planning something large so we can agree on direction before you write the code.

Things that would be useful:
- Supporting more file types in the treemap color scheme
- Better handling of Android/data paths on different OEM builds
- Tablet layout improvements
- Translations

Fork the repo, make your changes on a branch, and open a PR against `main`.

---

## Permissions

| Permission | Reason |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Full storage scan |
| `PACKAGE_USAGE_STATS` | App size breakdown |
| `QUERY_ALL_PACKAGES` | App list in Discover |
| `REQUEST_DELETE_PACKAGES` | APK uninstall |
| `POST_NOTIFICATIONS` | Scan progress |

---

## Tech

Kotlin, Jetpack Compose, Material 3 Expressive. No networking, analytics, or tracking. Everything runs on-device.

---

Made by Lavanbarath B / [Kreativ Devs](https://github.com/lavan8t)
