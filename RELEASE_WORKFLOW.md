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

---

## Release Notes Format

Every GitHub release uses one shape. CI generates it automatically from `CHANGELOG.md` and the
commit range — when a section is wrong, fix `CHANGELOG.md` and re-run, never hand-write the body.

| Field | Value |
| --- | --- |
| Tag | `v<versionName>-beta`, or `v<versionName>-beta-<short hash>` if that tag already exists |
| Title | `AndDirStat v<versionName> (Beta)`, or `AndDirStat v<versionName> (Beta) (<short hash>)` for a re-tag |
| Flag | Marked pre-release, never the "latest" release |
| Body | Template below |

### Body template

````markdown
### <section>

- <entry copied verbatim from CHANGELOG.md>

### Commits

- <commit subject> by @<author> in <short hash>

Download the compatible version for your device from the assets below.

---
**Architecture Downloads:**
- **arm64-v8a**: Modern 64-bit Android devices
````

### Rules

1. `CHANGELOG.md` headings carry over verbatim, in this order: `### Added`, `### Changed`, `### Fixed`, `### Performance`, `### Build`, `### Removed`. A section with no entries is omitted.
2. Every version must have its own `CHANGELOG.md` section. CI falls back to the tip commit message when it finds none, which produces an off-format body.
3. `### Commits` lists every commit in `<previous tag>..<tag>`; the first release lists the newest 10.
4. Architecture Downloads lists only the ABIs actually attached to that release, in the order arm64-v8a, armeabi-v7a, x86_64, universal. Keep it in sync with `splits.abi.include` in `app/build.gradle.kts`.
5. Never use a fenced block around the whole body, a bare `### Commit: <hash>` heading, or a `**Full Changelog**` link.

### Editing the workflow

`Create GitHub Release` is a YAML block scalar. Every line in that step — including heredoc bodies —
must stay indented to the same level as the surrounding script. A line starting at column 0 ends the
scalar and invalidates the whole workflow, and GitHub reports it only as a 0-second
"workflow file issue" failure that never reaches a runner.
