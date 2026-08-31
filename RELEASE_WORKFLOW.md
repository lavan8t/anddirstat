# Prod Release Workflow

This document defines the automated release lifecycle for **AndDirStat**.

---

## Architecture & Lifecycle

The release process is 100% automated via GitHub Actions (`.github/workflows/release.yml`):

```
       Local Development (main branch)
                     │
                     ▼
  Commit changes + Update CHANGELOG.md + Bump Version
                     │
                     ▼
        Push commit to 'prod' branch
   (git push origin main:prod --force)
                     │
                     ▼
   GitHub Action triggers on 'prod' branch:
   ├── Decodes release keystore
   ├── Builds split release APKs (arm64, armeabi-v7a, x86_64, universal)
   ├── Generates GitHub Release with APK assets & commit changelog
   └── Automatically DELETES the transient 'prod' branch
```

---

## Release Steps

1. **Verify Builds Locally**:
   ```bash
   ./gradlew compileDebugKotlin && ./gradlew assembleDebug
   ```

2. **Stage, Commit & Update Changelog**:
   - Update `app/build.gradle.kts` (`versionCode` & `versionName`).
   - Update `CHANGELOG.md` with features, changes, and bugfixes.
   - Commit changes to `main`:
     ```bash
     git add -A
     git commit -m "feat/fix: <description>"
     ```

3. **Deploy to Production**:
   - Push to the remote `prod` branch using SSL verification override if required by local CA:
     ```bash
     git -c http.sslVerify=false push origin main:prod --force
     ```
   - The CI runner on GitHub automatically executes `assembleRelease`, generates the GitHub release tagged `v<version>-beta`, and cleans up the `prod` branch.
