# AndDirStat

A storage analyzer for Android. It scans your internal storage and draws an interactive treemap so you can see exactly where your space went, then gives you tools to clean it up.

Requires Android 10 (API 29) or higher.

## What it does

**Treemap view.** Every file and folder gets a colored rectangle sized to its actual bytes. Tap any block to drill in. The whole tree redraws on selection so you always know where you are.

**Explorer.** A standard folder browser backed by the same scan, so navigating feels instant instead of waiting on the filesystem again.

**File types breakdown.** Shows total size per extension with bar charts. Useful for finding that you have 4 GB of `.mov` files you forgot about.

**Discover.** Search your storage by name or size threshold (`> 500MB`, `< 1KB`). Presets for starred files, files over 1 GB, old downloads, and APKs. Large apps get their own section.

**Cleaners.**
- Recycle bin with restore support
- Screenshot cleaner
- Duplicate file finder
- Empty folder remover
- Starred files manager

**External storage.** When you connect a USB drive or SD card, a centered chip appears below the app bar showing the device name. Tap it to list all connected volumes and map any one of them independently.

## Building

```bash
./gradlew assembleRelease
```

Release APKs land in `app/build/outputs/apk/release/`. The build produces separate APKs for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and a universal APK.

Signing reads from environment variables:

```
KEYSTORE_PATH
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

Without them, the build falls back to the debug signing config.

## Releasing

Push to `prod`. GitHub Actions builds all APKs, signs them, creates a beta release tagged `v{versionName}-beta`, attaches all four APKs, and deletes the `prod` branch when done. The release description pulls from the last commit message, so write your changelog there before pushing.

Secrets are stored in GitHub Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Permissions

| Permission | Reason |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Full storage scan |
| `PACKAGE_USAGE_STATS` | App size breakdown |
| `QUERY_ALL_PACKAGES` | App list in Discover |
| `REQUEST_DELETE_PACKAGES` | APK uninstall |
| `POST_NOTIFICATIONS` | Scan progress notification |

## Tech

Kotlin, Jetpack Compose, Material 3. No third-party networking, analytics, or tracking. All scanning runs on-device.

---

Made by Lavanbarath B / [Kreativ Devs](https://github.com/lavan8t)
