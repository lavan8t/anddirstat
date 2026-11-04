# Modern Disk Cleanup Improvements (Inspired by disktree) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate modern disk cleanup improvements inspired by `tobi/disktree` into AndDirStat—including a staged cleanup queue with real-time reclaimed space calculation and dedicated review sheet, semantic data grouping with visual diagonal hatching on reclaimable canvas tiles, automated cleanup suggestions, and strict Android safe deletion boundaries—without modifying existing visual styling, navigation layout, or core architecture.

**Architecture:** A domain-specific cleanup subsystem (`com.kd.anddirstat.cleanup`) that provides pure domain models and logic for safety boundary validation, semantic categorization, queue staging, and suggestion aggregation. This subsystem cleanly integrates into the existing `TreemapCanvas` (diagonal hatching overlay for reclaimable space), `MainActivity` state holders (staged queue state), `FileMaintenanceEngine` (boundary enforcement), and UI layers (`TreemapNavPill`, `TreemapNavRail`, and a new `CleanupQueueReviewSheet`).

**Tech Stack:** Kotlin, Android SDK (minSdk 29, targetSdk 37), Jetpack Compose, Material Design 3, Kotlin Coroutines, JUnit 4.

---

## Global Constraints & Principles

1. **Zero UI Theme or Navigation Drift:** Keep existing colors, Material 3 tokens, font styling, and navigation layouts (Nameless bottom pill in portrait, navigation rail in landscape, and adaptive window sizing).
2. **Zero Performance Regression:** Treemap rendering must maintain smooth 60fps interaction during pinch-to-zoom and pan. Static caching via `TreemapBitmapCache` must be preserved.
3. **No External Dependencies:** Implement all features (including diagonal hatching and safety boundary checks) using existing Android SDK and Compose libraries.
4. **Android-Specific Deletion Safety:** Strictly forbid deletion of mount points, volume roots (e.g. `/storage/emulated/0`), Android system trees (`/system`, `/vendor`, `/apex`, `/data/system`), root framework containers (`Android/`, `Android/data`), system packages, and user-starred files.
5. **No Speculative Rewrites:** Enhance existing components (`TreemapCanvas`, `MainActivity`, `FileMaintenanceEngine`, `TreemapNavPill`) through targeted integration without rewriting working logic.

---

## Architectural Analysis & Integration Map

### 1. Repository Audit Summary

- **Build Configuration & Targets:** `app/build.gradle.kts` specifies `minSdk = 29` (Android 10), `compileSdk = 37`, `targetSdk = 37`, Jetpack Compose BOM, Material Design 3, Java 11.
- **Data Models:** `model/Models.kt` defines `CompactNode` (`name: String`, `isDirectory: Boolean`, `size: Long`, `children: Array<CompactNode>?`). `CompactNode` is memory-efficient and deliberately does not store full paths per node. Paths are dynamically assembled during traversal or navigation.
- **Scanner & Filters:**
  - `StorageScanner.kt`: Multi-threaded parallel file scanner using a dedicated coroutine dispatcher sized to CPU cores. App package sizes are resolved via `StorageStatsManager`.
  - `StorageFilterHelper.kt`: Provides tree filtering (`filterStorageTree`), extension aggregation (`aggregateExtensionStats`), top files collection (`aggregateTopFiles`), screenshot discovery (`getScreenshots`), duplicate detection (`findDuplicates`), and node pruning (`pruneNodes`).
- **Treemap Rendering Pipeline:**
  - `TreemapCanvas.kt`: Computes squarified treemap tiles (`computeTreemapTiles`, `layoutSquarified`).
  - Static mode (`scale <= 1.001f`): Renders tiles off-thread to an `ImageBitmap` cached in `TreemapBitmapCache` using Android `Canvas`.
  - Zoom/Pan mode: Hardware-accelerated direct vector drawing in Compose `Canvas` with viewport frustum culling.
- **Deletion & Storage Operations:**
  - `FileMaintenanceEngine.kt`: Handles `deleteTreeNodes` and `batchDelete`, moving items to Recycle Bin (`.trashed-*` pattern + MediaStore updates) or permanent removal, skipping starred files (`FavoritesManager`).
  - `MainActivity.kt`: Root composable holding `selectedTreeNodes: Map<CompactNode, String>` and triggering `TreeDeleteDialog` and `DeletionProgressDialog`.

---

### 2. Feature Architecture & Attachment Points

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MainActivity Compose UI                         │
│                                                                        │
│  ┌───────────────────────┐             ┌────────────────────────────┐  │
│  │     TreemapScreen     │             │       DiscoverScreen       │  │
│  │  ┌─────────────────┐  │             │  ┌──────────────────────┐  │  │
│  │  │  TreemapCanvas  │  │             │  │  CleanupSuggestions  │  │  │
│  │  │ (Hatch Overlay) │  │             │  │      Card/Screen     │  │  │
│  │  └─────────────────┘  │             │  └──────────────────────┘  │  │
│  └───────────────────────┘             └────────────────────────────┘  │
│             │                                         │                │
│             ▼                                         ▼                │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │            Staged Cleanup Queue State & Actions              │     │
│   │   - stagedQueue: Map<CompactNode, StagedQueueItem>           │     │
│   │   - projectedReclaimedBytes: Long                            │     │
│   └──────────────────────────────────────────────────────────────┘     │
│             │                                         │                │
│             ▼                                         ▼                │
│   ┌───────────────────────────┐         ┌──────────────────────────┐   │
│   │  TreemapNavPill / Rail    │         │ CleanupQueueReviewSheet  │   │
│   │  (Queue Counter & Pill)   │         │ (Inspect, Unmark, Commit)│   │
│   └───────────────────────────┘         └──────────────────────────┘   │
└──────────────────────────────────────┬─────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Domain Cleanup Subsystem                        │
│                     (com.kd.anddirstat.cleanup)                        │
│                                                                        │
│  ┌─────────────────────────┐             ┌──────────────────────────┐  │
│  │   SemanticClassifier    │             │   DeletionSafetyGuard    │  │
│  │ - Logical categories    │             │ - Block volume roots     │  │
│  │ - isReclaimable()       │             │ - Block system trees     │  │
│  └─────────────────────────┘             │ - Block Android/data     │  │
│                                          └──────────────────────────┘  │
│  ┌─────────────────────────┐             ┌──────────────────────────┐  │
│  │ CleanupSuggestionsEngine│             │  FileMaintenanceEngine   │  │
│  │ - App & System Caches   │             │ - Execute batch commit   │  │
│  │ - Obsolete APKs         │             │ - MediaStore sync        │  │
│  │ - Temporary/Stale files │             │ - Prune memory tree      │  │
│  └─────────────────────────┘             └──────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

#### Feature A: Safe Deletion Boundaries (`DeletionSafetyGuard`)
- **Attach Point:** New class `com.kd.anddirstat.cleanup.DeletionSafetyGuard`. Invoked in `FileMaintenanceEngine` before any file or node deletion, and in `MainActivity` before adding items to the cleanup queue.
- **Rules (inspired by `disktree`'s `removal.rs`):**
  1. Filesystem root (`/`), volume roots (`/storage/emulated/0`, `/storage/emulated`, external SD mounts) are rejected.
  2. Core system trees (`/system`, `/vendor`, `/product`, `/apex`, `/boot`, `/data/system`, `/data/dalvik-cache`, `/data`) are rejected.
  3. Android core container directories (`/storage/emulated/0/Android`, `/storage/emulated/0/Android/data`, `/storage/emulated/0/Android/obb`) cannot be deleted in whole.
  4. System packages (`ApplicationInfo.FLAG_SYSTEM`) cannot be uninstalled.
  5. Synthetic UI container nodes (`[System & OS]`, `[Free Space]`, `Apps & System Packages`) are rejected.
  6. Starred paths (`FavoritesManager.isStarred`) are rejected.

#### Feature B: Semantic Data Grouping & Reclaimable Space Markers (`SemanticClassifier` + `TreemapCanvas`)
- **Attach Point:** New class `com.kd.anddirstat.cleanup.SemanticClassifier` and modification to `com.kd.anddirstat.treemap.TreemapCanvas`.
- **Classification:**
  - `CACHE_TEMP`: Path contains `/cache/`, `/.cache/`, `/temp/`, `/tmp/`, `/.thumbnails/`, or ends with `.tmp`, `.temp`, `.log`, `.crdownload`, `.part`, `.dmp`, `.dump`, `.trace`, `.bak`, `.nomedia`, or node is `Cache` / `App Cache` / `[Temporary System Files]`. **Marked as Reclaimable.**
  - `TRASH`: `.trashed-*`, `[Recycle Bin]`, `.trash`. **Marked as Reclaimable.**
  - `OBSOLETE_APK`: `.apk` files inside `Download/` or `Downloads/`. **Marked as Reclaimable.**
  - `DOWNLOADS`: Files in `Download/` or `Downloads/` (excluding obsolete APKs).
  - `MEDIA`: `DCIM/`, `Pictures/`, `Movies/`, `Music/` or media file extensions.
  - `DOCUMENTS`: `Documents/` or document file extensions.
  - `APP_DATA`: `Android/data/`, app data nodes.
  - `ARCHIVES`: `.zip`, `.rar`, `.7z`, `.tar.gz`, etc.
  - `SYSTEM`: `[System & OS]`.
  - `OTHER`: Uncategorized files.
- **Treemap Reclaimable Marker Pattern:**
  - Add `isReclaimable: Boolean` to `TreemapTile`.
  - In `renderTreemapToBitmap` (static snapshot) and in `TreemapCanvas` (dynamic vector draw):
    When `tile.isReclaimable == true`, draw a subtle 45-degree diagonal hatching overlay (`Color.White.copy(alpha = 0.18f)` / dark mode contrast) using a hardware-compatible pattern or repeated line drawing over the tile rect. The tile retains its base color/shading while immediately signaling reclaimable space.

#### Feature C: Staging Cleanup Queue & Dedicated Review Sheet
- **Attach Point:** `com.kd.anddirstat.cleanup.StagedQueueItem`, `MainActivity.kt`, `TreemapNavPill.kt`, `TreemapNavRail.kt`, and new composable `ui/components/CleanupQueueReviewSheet.kt`.
- **Queue State:**
  - Holds `stagedQueue: Map<CompactNode, StagedQueueItem>`.
  - Real-time `projectedReclaimedBytes = stagedQueue.values.sumOf { it.reclaimableBytes }`.
- **UI Experience:**
  - In Treemap Tile Detail Popup: replace direct single-item delete with **"Stage for Cleanup"** / **"Unstage"** (with visual badge if already staged). If blocked by `DeletionSafetyGuard`, the action is disabled with an explanatory tooltip/label.
  - In `TreemapNavPill` and `TreemapNavRail`: when items are staged, show the staged count, projected reclaimed size, clear button, and a **"Review"** button.
  - Clicking "Review" opens `CleanupQueueReviewSheet` (a Material 3 Modal Bottom Sheet):
    - Lists all staged items with name, path, formatted size, and semantic category icon.
    - Allows unmarking individual items with an "X" icon.
    - Provides a deletion mode toggle: **"Move to Recycle Bin"** (reversible, default) vs **"Permanent Deletion"**.
    - Displays total reclaimable space badge.
    - **"Commit Cleanup"** button runs batch execution through `FileMaintenanceEngine`, records reclaimed bytes via `StorageTrendManager`, prunes memory nodes from `rootNode`, and closes the sheet.

#### Feature D: Cleanup Suggestions Engine & UI Section
- **Attach Point:** New class `com.kd.anddirstat.cleanup.CleanupSuggestionsEngine`, new screen `ui/screens/cleaners/CleanupSuggestionsScreen.kt`, and addition to `DiscoverScreen.kt`.
- **Suggestion Engine:**
  - Analyzes the scanned `CompactNode` tree to identify high-confidence safe deletion candidates:
    1. **App & System Caches:** Caches and temporary system files.
    2. **Residual & Temp Files:** Stale `.tmp`, `.log`, `.crdownload`, `.thumbnails`.
    3. **Dangling Downloaded APKs:** APK installers left in `Download/`.
    4. **Recycle Bin:** Files already marked as `.trashed-*`.
    5. **Duplicate Files:** Leveraging `StorageFilterHelper.findDuplicates`.
  - Produces grouped suggestions with item counts, total sizes, and candidate lists.
- **UI Integration:**
  - In `DiscoverScreen.kt`: Add a "Cleanup Suggestions" card highlighting total safe reclaimable space (e.g. *"2.4 GB can be reclaimed"*).
  - Tapping opens `CleanupSuggestionsScreen`: categorized cards with "Stage All" or individual item staging directly into the Cleanup Queue.

---

## Files to Modify and Create

### New Files to Create
1. `app/src/main/java/com/kd/anddirstat/cleanup/DeletionSafetyGuard.kt`
   - Safe deletion rules, path validators, and system boundary enforcement.
2. `app/src/main/java/com/kd/anddirstat/cleanup/SemanticClassifier.kt`
   - Category enums, path/node categorization, and `isReclaimable()` evaluation.
3. `app/src/main/java/com/kd/anddirstat/cleanup/CleanupModels.kt`
   - Data classes: `StagedQueueItem`, `CleanupSuggestionGroup`, `CleanupSuggestionsReport`.
4. `app/src/main/java/com/kd/anddirstat/cleanup/CleanupSuggestionsEngine.kt`
   - Tree traversal and candidate aggregator for smart suggestions.
5. `app/src/main/java/com/kd/anddirstat/ui/components/CleanupQueueReviewSheet.kt`
   - Dedicated review bottom sheet for inspecting, unmarking, selecting mode, and committing batch cleanup.
6. `app/src/main/java/com/kd/anddirstat/ui/screens/cleaners/CleanupSuggestionsScreen.kt`
   - Dedicated UI screen for exploring categorized safe cleanup candidates with one-tap staging.
7. `app/src/test/java/com/kd/anddirstat/cleanup/DeletionSafetyGuardTest.kt`
   - Unit tests verifying critical Android boundaries (root paths, system trees, volume roots, starred files).
8. `app/src/test/java/com/kd/anddirstat/cleanup/SemanticClassifierTest.kt`
   - Unit tests verifying classification of caches, temporary files, media, downloads, and reclaimable markers.
9. `app/src/test/java/com/kd/anddirstat/cleanup/CleanupSuggestionsEngineTest.kt`
   - Unit tests verifying suggestions extraction and duplicate aggregation.

### Existing Files to Modify
1. `app/src/main/java/com/kd/anddirstat/model/Models.kt`
   - Add `AppDestinations.CLEANUP_SUGGESTIONS = "cleanup_suggestions"`.
2. `app/src/main/java/com/kd/anddirstat/treemap/TreemapCanvas.kt`
   - Add `isReclaimable: Boolean` to `TreemapTile`.
   - Update `computeTreemapTiles` to populate `isReclaimable` via `SemanticClassifier`.
   - Implement diagonal hatching rendering in `renderTreemapToBitmap` (software bitmap Canvas) and in `TreemapCanvas` (Compose hardware Canvas).
3. `app/src/main/java/com/kd/anddirstat/util/FileMaintenanceEngine.kt`
   - Integrate `DeletionSafetyGuard.validateDeletion` into `batchDelete` and `deleteTreeNodes` to guarantee hard blocks.
4. `app/src/main/java/com/kd/anddirstat/ui/components/TreemapNavPill.kt` & `TreemapNavRail.kt`
   - Adapt selection indicator into the Staging Cleanup Queue bar with "Review" action and real-time reclaimable size.
5. `app/src/main/java/com/kd/anddirstat/MainActivity.kt`
   - Track `stagedCleanupQueue: Map<CompactNode, StagedQueueItem>`.
   - Update tile detail popup to support "Stage for Cleanup", display safety status, and trigger the `CleanupQueueReviewSheet`.
   - Register route for `AppDestinations.CLEANUP_SUGGESTIONS`.
6. `app/src/main/java/com/kd/anddirstat/ui/screens/DiscoverScreen.kt`
   - Insert "Cleanup Suggestions" card linking to `CleanupSuggestionsScreen`.

---

## Sequential Implementation Tasks

### Task 1: Safe Deletion Boundaries (`DeletionSafetyGuard`)

**Files:**
- Create: `app/src/main/java/com/kd/anddirstat/cleanup/DeletionSafetyGuard.kt`
- Create: `app/src/test/java/com/kd/anddirstat/cleanup/DeletionSafetyGuardTest.kt`
- Modify: `app/src/main/java/com/kd/anddirstat/util/FileMaintenanceEngine.kt:20-85`

**Interfaces:**
- Produces: `DeletionSafetyGuard.checkSafety(node: CompactNode?, path: String, context: Context?): SafetyResult`
- Produces: `sealed class SafetyResult { object Allowed : SafetyResult(); data class Blocked(val reason: String) : SafetyResult() }`

- [ ] **Step 1: Write the failing unit tests for `DeletionSafetyGuard`**

```kotlin
// app/src/test/java/com/kd/anddirstat/cleanup/DeletionSafetyGuardTest.kt
package com.kd.anddirstat.cleanup

import com.kd.anddirstat.model.CompactNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionSafetyGuardTest {

    @Test
    fun testRootPathsAreBlocked() {
        val rootNode = CompactNode(name = "storage", isDirectory = true)
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(rootNode, "/"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(rootNode, "/storage/emulated/0"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(rootNode, "/storage/emulated/0/"))
    }

    @Test
    fun testCriticalSystemDirectoriesAreBlocked() {
        val sysNode = CompactNode(name = "system", isDirectory = true)
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysNode, "/system"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysNode, "/system/bin/sh"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysNode, "/vendor/lib"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysNode, "/data/system"))
    }

    @Test
    fun testAndroidFrameworkRootContainersAreBlocked() {
        val androidNode = CompactNode(name = "Android", isDirectory = true)
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(androidNode, "/storage/emulated/0/Android"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(androidNode, "/storage/emulated/0/Android/data"))
    }

    @Test
    fun testSyntheticContainersAreBlocked() {
        val freeSpace = CompactNode(name = "[Free Space]", isDirectory = false)
        val sysOs = CompactNode(name = "[System & OS]", isDirectory = false)
        val apps = CompactNode(name = "Apps & System Packages", isDirectory = true)
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(freeSpace, "[Free Space]"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysOs, "[System & OS]"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(apps, "Apps & System Packages"))
    }

    @Test
    fun testUserFilesAreAllowed() {
        val userFile = CompactNode(name = "video.mp4", isDirectory = false)
        val userFolder = CompactNode(name = "MyFolder", isDirectory = true)
        assertTrue(DeletionSafetyGuard.isDeletionAllowed(userFile, "/storage/emulated/0/Download/video.mp4"))
        assertTrue(DeletionSafetyGuard.isDeletionAllowed(userFolder, "/storage/emulated/0/DCIM/Camera"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.kd.anddirstat.cleanup.DeletionSafetyGuardTest`
Expected: FAIL with compilation error (class not found).

- [ ] **Step 3: Implement `DeletionSafetyGuard.kt`**

```kotlin
// app/src/main/java/com/kd/anddirstat/cleanup/DeletionSafetyGuard.kt
package com.kd.anddirstat.cleanup

import android.content.Context
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import java.io.File

sealed class SafetyResult {
    object Allowed : SafetyResult()
    data class Blocked(val reason: String) : SafetyResult()
}

object DeletionSafetyGuard {

    private val BLOCKED_EXACT_PATHS = setOf(
        "/",
        "/storage",
        "/storage/emulated",
        "/storage/emulated/0",
        "/storage/emulated/0/",
        "/sdcard",
        "/sdcard/",
        "/data",
        "/system",
        "/vendor",
        "/product",
        "/apex",
        "/boot",
        "/recovery",
        "/data/system",
        "/data/dalvik-cache"
    )

    private val BLOCKED_PREFIXES = listOf(
        "/system/",
        "/vendor/",
        "/product/",
        "/apex/",
        "/boot/",
        "/data/system/",
        "/data/dalvik-cache/"
    )

    fun checkSafety(node: CompactNode?, path: String, context: Context? = null): SafetyResult {
        val cleanPath = path.trim().replace('\\', '/')
        val name = node?.name?.trim() ?: cleanPath.substringAfterLast('/')

        // 1. Synthetic and virtual containers
        if (name == "[Free Space]" || name == "Free Space" ||
            name == "[System & OS]" || name == "System & OS" ||
            name == "Apps & System Packages" || cleanPath == "Apps & System Packages"
        ) {
            return SafetyResult.Blocked("System virtual items cannot be deleted")
        }

        // 2. Exact blocked root paths
        val normalized = cleanPath.removeSuffix("/")
        if (BLOCKED_EXACT_PATHS.contains(normalized) || BLOCKED_EXACT_PATHS.contains("$normalized/")) {
            return SafetyResult.Blocked("Root storage mounts cannot be deleted")
        }

        // 3. System partitions and directories
        for (prefix in BLOCKED_PREFIXES) {
            if (cleanPath.startsWith(prefix, ignoreCase = true)) {
                return SafetyResult.Blocked("Android system files are protected")
            }
        }

        // 4. Android framework root containers
        if (normalized.equals("/storage/emulated/0/Android", ignoreCase = true) ||
            normalized.equals("/storage/emulated/0/Android/data", ignoreCase = true) ||
            normalized.equals("/storage/emulated/0/Android/obb", ignoreCase = true) ||
            cleanPath.equals("Android", ignoreCase = true) && node?.isDirectory == true
        ) {
            return SafetyResult.Blocked("Core Android app directories cannot be deleted in bulk")
        }

        // 5. Volume roots check
        if (context != null) {
            val volumes = FileUtils.getAvailableStorageVolumes(context)
            for (vol in volumes) {
                val volPath = vol.path.absolutePath.removeSuffix("/")
                if (normalized.equals(volPath, ignoreCase = true)) {
                    return SafetyResult.Blocked("Storage volume roots cannot be deleted")
                }
            }
            // 6. User starred protection
            if (FavoritesManager.isStarred(context, cleanPath)) {
                return SafetyResult.Blocked("Item is starred and protected")
            }
        }

        return SafetyResult.Allowed
    }

    fun isDeletionAllowed(node: CompactNode?, path: String, context: Context? = null): Boolean {
        return checkSafety(node, path, context) is SafetyResult.Allowed
    }
}
```

- [ ] **Step 4: Wire `DeletionSafetyGuard` into `FileMaintenanceEngine.kt`**

In `app/src/main/java/com/kd/anddirstat/util/FileMaintenanceEngine.kt`:
Update `batchDelete` and `deleteTreeNodes` to check `DeletionSafetyGuard.checkSafety`:
```kotlin
val (safeItems, blockedItems) = items.partition { (node, path) ->
    DeletionSafetyGuard.isDeletionAllowed(node, path, context)
}
if (safeItems.isEmpty() && blockedItems.isNotEmpty()) {
    AppNotifier.notify("Deletion blocked: Protected system paths selected.")
    return@withContext 0
}
```

- [ ] **Step 5: Run tests and verify they pass**

Run: `./gradlew testDebugUnitTest --tests com.kd.anddirstat.cleanup.DeletionSafetyGuardTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kd/anddirstat/cleanup/DeletionSafetyGuard.kt app/src/test/java/com/kd/anddirstat/cleanup/DeletionSafetyGuardTest.kt app/src/main/java/com/kd/anddirstat/util/FileMaintenanceEngine.kt
git commit -m "feat(cleanup): add safe deletion boundaries and validation guard"
```

---

### Task 2: Semantic Data Grouping & Reclaimable Space Markers

**Files:**
- Create: `app/src/main/java/com/kd/anddirstat/cleanup/SemanticClassifier.kt`
- Create: `app/src/test/java/com/kd/anddirstat/cleanup/SemanticClassifierTest.kt`
- Modify: `app/src/main/java/com/kd/anddirstat/treemap/TreemapCanvas.kt:220-350,630-795`

**Interfaces:**
- Produces: `enum class SemanticCategory`
- Produces: `SemanticClassifier.classify(node: CompactNode, path: String): SemanticCategory`
- Produces: `SemanticClassifier.isReclaimable(node: CompactNode, path: String): Boolean`
- Produces: `TreemapTile.isReclaimable: Boolean`

- [ ] **Step 1: Write failing unit tests for `SemanticClassifier`**

```kotlin
// app/src/test/java/com/kd/anddirstat/cleanup/SemanticClassifierTest.kt
package com.kd.anddirstat.cleanup

import com.kd.anddirstat.model.CompactNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SemanticClassifierTest {

    @Test
    fun testCacheAndTemporaryFilesAreReclaimable() {
        val cacheDir = CompactNode("cache", isDirectory = true)
        val tmpFile = CompactNode("session.tmp", isDirectory = false)
        val logFile = CompactNode("app.log", isDirectory = false)
        val crdownloadFile = CompactNode("video.mp4.crdownload", isDirectory = false)

        assertTrue(SemanticClassifier.isReclaimable(cacheDir, "/storage/emulated/0/Android/data/com.foo/cache"))
        assertTrue(SemanticClassifier.isReclaimable(tmpFile, "/storage/emulated/0/Download/session.tmp"))
        assertTrue(SemanticClassifier.isReclaimable(logFile, "/storage/emulated/0/Download/app.log"))
        assertTrue(SemanticClassifier.isReclaimable(crdownloadFile, "/storage/emulated/0/Download/video.mp4.crdownload"))
    }

    @Test
    fun testTrashItemsAreReclaimable() {
        val trashedFile = CompactNode(".trashed-123-photo.jpg", isDirectory = false)
        val recycleBin = CompactNode("[Recycle Bin]", isDirectory = true)

        assertTrue(SemanticClassifier.isReclaimable(trashedFile, "/storage/emulated/0/DCIM/.trashed-123-photo.jpg"))
        assertTrue(SemanticClassifier.isReclaimable(recycleBin, "[Recycle Bin]"))
    }

    @Test
    fun testObsoleteApkInDownloadsIsReclaimable() {
        val apkNode = CompactNode("update.apk", isDirectory = false)
        assertTrue(SemanticClassifier.isReclaimable(apkNode, "/storage/emulated/0/Download/update.apk"))
    }

    @Test
    fun testNormalUserMediaIsNotReclaimable() {
        val photo = CompactNode("photo.jpg", isDirectory = false)
        val doc = CompactNode("resume.pdf", isDirectory = false)

        assertFalse(SemanticClassifier.isReclaimable(photo, "/storage/emulated/0/DCIM/Camera/photo.jpg"))
        assertFalse(SemanticClassifier.isReclaimable(doc, "/storage/emulated/0/Documents/resume.pdf"))
    }

    @Test
    fun testCategoryClassification() {
        assertEquals(SemanticCategory.CACHE_TEMP, SemanticClassifier.classify(CompactNode("cache", true), "/app/cache"))
        assertEquals(SemanticCategory.MEDIA, SemanticClassifier.classify(CompactNode("song.mp3", false), "/Music/song.mp3"))
        assertEquals(SemanticCategory.DOCUMENTS, SemanticClassifier.classify(CompactNode("report.pdf", false), "/Documents/report.pdf"))
        assertEquals(SemanticCategory.DOWNLOADS, SemanticClassifier.classify(CompactNode("dataset.csv", false), "/Download/dataset.csv"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.kd.anddirstat.cleanup.SemanticClassifierTest`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement `SemanticClassifier.kt`**

```kotlin
// app/src/main/java/com/kd/anddirstat/cleanup/SemanticClassifier.kt
package com.kd.anddirstat.cleanup

import com.kd.anddirstat.model.CompactNode

enum class SemanticCategory(val title: String, val icon: String) {
    CACHE_TEMP("Caches & Temp", "cached"),
    TRASH("Recycle Bin", "delete"),
    OBSOLETE_APK("APK Installers", "android"),
    DOWNLOADS("Downloads", "file_download"),
    MEDIA("Media", "movie"),
    DOCUMENTS("Documents", "description"),
    APP_DATA("App Data", "apps"),
    ARCHIVES("Archives", "folder_zip"),
    SYSTEM("System & OS", "settings"),
    OTHER("Other", "insert_drive_file")
}

object SemanticClassifier {

    private val CACHE_NAMES = setOf("cache", ".cache", "temp", "tmp", "thumbnails", ".thumbnails")
    private val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "crdownload", "part", "dmp", "dump", "trace", "bak")

    fun isReclaimable(node: CompactNode, path: String): Boolean {
        val nameLower = node.name.lowercase()
        val pathLower = path.lowercase().replace('\\', '/')

        if (nameLower.startsWith(".trashed") || nameLower == ".trash" || nameLower == "[recycle bin]" || pathLower.contains("[recycle bin]")) {
            return true
        }

        if (nameLower == "cache" || nameLower == "app cache" || nameLower == "[temporary system files]" || nameLower == "temporary system files") {
            return true
        }

        if (pathLower.contains("/cache/") || pathLower.contains("/.cache/") ||
            pathLower.contains("/temp/") || pathLower.contains("/tmp/") ||
            pathLower.contains("/.thumbnails/") || pathLower.contains("/thumbnails/")
        ) {
            return true
        }

        val ext = nameLower.substringAfterLast('.', "")
        if (TEMP_EXTENSIONS.contains(ext) || nameLower == "thumbs.db" || nameLower == ".nomedia") {
            return true
        }

        if (ext in setOf("apk", "xapk", "apks") && pathLower.contains("/download")) {
            return true
        }

        return false
    }

    fun classify(node: CompactNode, path: String): SemanticCategory {
        val nameLower = node.name.lowercase()
        val pathLower = path.lowercase().replace('\\', '/')
        val ext = nameLower.substringAfterLast('.', "")

        return when {
            nameLower.startsWith(".trashed") || nameLower == "[recycle bin]" -> SemanticCategory.TRASH
            isReclaimable(node, path) && (ext in setOf("apk", "xapk") && pathLower.contains("/download")) -> SemanticCategory.OBSOLETE_APK
            isReclaimable(node, path) -> SemanticCategory.CACHE_TEMP
            nameLower == "[system & os]" || pathLower.contains("[system & os]") -> SemanticCategory.SYSTEM
            pathLower.contains("android/data") || pathLower.contains("android/obb") || pathLower.contains("apps & system packages") -> SemanticCategory.APP_DATA
            pathLower.contains("/download") || pathLower.contains("downloads") -> SemanticCategory.DOWNLOADS
            ext in setOf("mp4", "mkv", "avi", "mov", "webm", "mp3", "flac", "wav", "m4a", "jpg", "jpeg", "png", "webp", "gif") -> SemanticCategory.MEDIA
            ext in setOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub") -> SemanticCategory.DOCUMENTS
            ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso") -> SemanticCategory.ARCHIVES
            else -> SemanticCategory.OTHER
        }
    }
}
```

- [ ] **Step 4: Update `TreemapCanvas.kt` with `isReclaimable` and Diagonal Hatching**

1. Update `TreemapTile`:
```kotlin
class TreemapTile(
    val node: CompactNode,
    val path: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val isApp: Boolean,
    val pkgName: String?,
    val baseColor: Color,
    val gradientColors: List<Color>,
    val isReclaimable: Boolean = false
)
```
2. Populate `isReclaimable = SemanticClassifier.isReclaimable(node, currentPath)` in `computeTreemapTiles`.
3. In `renderTreemapToBitmap` (static snapshot), draw diagonal hatching over reclaimable tiles:
```kotlin
if (tile.isReclaimable && sW >= 4f && sH >= 4f) {
    paint.shader = null
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 1.2f
    paint.color = if (isDark) 0x33FFFFFF else 0x2B000000
    val step = 12f
    var d = -sH
    while (d < sW) {
        val x1 = (sLeft + d).coerceIn(sLeft, sLeft + sW)
        val y1 = (sTop + (sLeft - x1) + d).coerceIn(sTop, sTop + sH)
        val x2 = (sLeft + d + sH).coerceIn(sLeft, sLeft + sW)
        val y2 = (sTop + (sLeft - x2) + d + sH).coerceIn(sTop, sTop + sH)
        canvas.drawLine(x1, y1, x2, y2, paint)
        d += step
    }
}
```
4. In `TreemapCanvas` (Compose `DrawScope` on zoom/pan), apply matching diagonal hatch lines clipped to `drawRect`:
```kotlin
if (tile.isReclaimable && drawW >= 4f && drawH >= 4f) {
    val hatchColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.16f)
    clipRect(sLeft, sTop, sLeft + drawW, sTop + drawH) {
        val step = 12f
        var d = -drawH
        while (d < drawW) {
            drawLine(
                color = hatchColor,
                start = Offset(sLeft + d, sTop),
                end = Offset(sLeft + d + drawH, sTop + drawH),
                strokeWidth = 1.2f
            )
            d += step
        }
    }
}
```

- [ ] **Step 5: Run tests and verify**

Run: `./gradlew testDebugUnitTest --tests com.kd.anddirstat.cleanup.SemanticClassifierTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kd/anddirstat/cleanup/SemanticClassifier.kt app/src/test/java/com/kd/anddirstat/cleanup/SemanticClassifierTest.kt app/src/main/java/com/kd/anddirstat/treemap/TreemapCanvas.kt
git commit -m "feat(cleanup): add semantic classification and diagonal hatching on reclaimable treemap tiles"
```

---

### Task 3: Staging Cleanup Queue & Dedicated Review Sheet

**Files:**
- Create: `app/src/main/java/com/kd/anddirstat/cleanup/CleanupModels.kt`
- Create: `app/src/main/java/com/kd/anddirstat/ui/components/CleanupQueueReviewSheet.kt`
- Modify: `app/src/main/java/com/kd/anddirstat/ui/components/TreemapNavPill.kt:70-420`
- Modify: `app/src/main/java/com/kd/anddirstat/ui/components/TreemapNavRail.kt:70-170`
- Modify: `app/src/main/java/com/kd/anddirstat/MainActivity.kt:230-260,720-760,1090-1250`

**Interfaces:**
- Produces: `data class StagedQueueItem(val node: CompactNode, val path: String, val reclaimableBytes: Long, val category: SemanticCategory, val isApp: Boolean, val pkgName: String?)`
- Produces: `CleanupQueueReviewSheet(stagedItems: List<StagedQueueItem>, onUnstage: (StagedQueueItem) -> Unit, onClearAll: () -> Unit, onCommitCleanup: (isPermanent: Boolean) -> Unit, onDismiss: () -> Unit)`

- [ ] **Step 1: Create `CleanupModels.kt`**

```kotlin
// app/src/main/java/com/kd/anddirstat/cleanup/CleanupModels.kt
package com.kd.anddirstat.cleanup

import com.kd.anddirstat.model.CompactNode

data class StagedQueueItem(
    val node: CompactNode,
    val path: String,
    val reclaimableBytes: Long,
    val category: SemanticCategory,
    val isApp: Boolean = false,
    val pkgName: String? = null
)

data class CleanupSuggestionGroup(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val totalBytes: Long,
    val items: List<StagedQueueItem>
)

data class CleanupSuggestionsReport(
    val totalReclaimableBytes: Long,
    val totalItemCount: Int,
    val groups: List<CleanupSuggestionGroup>
)
```

- [ ] **Step 2: Create `CleanupQueueReviewSheet.kt`**

Create `app/src/main/java/com/kd/anddirstat/ui/components/CleanupQueueReviewSheet.kt` using standard Material 3 `ModalBottomSheet`:
- Header: Title "Cleanup Queue", item count, and total projected reclaimed space badge.
- Segmented Deletion Mode: "Recycle Bin" (safe, reversible) vs "Permanent Delete" (immediate space reclaim).
- LazyColumn listing staged items with category icons, item names, middle-ellipsized paths, file sizes, and an unstage close button.
- "Unstage All" text button and a prominent "Commit Cleanup (Freed X MB)" button styled with `MaterialTheme.colorScheme.error` or `primary`.

- [ ] **Step 3: Update `TreemapNavPill.kt` & `TreemapNavRail.kt`**

- In both components, change the delete button action from immediate delete confirmation dialog to opening the **Cleanup Queue Review Sheet**.
- Display:
  - Staged item count and real-time total reclaimed bytes: `"${stagedQueue.size} staged • ${FileUtils.formatFileSize(totalBytes)} reclaimable"`.
  - Button label: "Review" with badge icon instead of raw "Delete".

- [ ] **Step 4: Update `MainActivity.kt` Detail Popup & Queue Management**

- Replace/augment `selectedTreeNodes` with `stagedQueue: Map<CompactNode, StagedQueueItem>`.
- In detail popup:
  - Check `DeletionSafetyGuard.checkSafety(node, path, context)`.
  - If blocked: show a security shield icon and reason text ("Protected system path").
  - If allowed: show "Stage for Cleanup" (or "Unstage" if already in queue) with live reclaimed space update.
- When user taps "Review" from Nav Pill/Rail, launch `CleanupQueueReviewSheet`.
- On Commit: invoke `FileMaintenanceEngine.deleteTreeNodes(...)`, update `StorageTrendManager.recordFreedBytes`, prune removed nodes via `StorageFilterHelper.pruneNodes`, and notify user via `AppNotifier`.

- [ ] **Step 5: Run unit tests and build check**

Run: `./gradlew compileDebugKotlin testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kd/anddirstat/cleanup/CleanupModels.kt app/src/main/java/com/kd/anddirstat/ui/components/CleanupQueueReviewSheet.kt app/src/main/java/com/kd/anddirstat/ui/components/TreemapNavPill.kt app/src/main/java/com/kd/anddirstat/ui/components/TreemapNavRail.kt app/src/main/java/com/kd/anddirstat/MainActivity.kt
git commit -m "feat(cleanup): implement staging cleanup queue with dedicated review bottom sheet"
```

---

### Task 4: Cleanup Suggestions Engine & UI Screen

**Files:**
- Create: `app/src/main/java/com/kd/anddirstat/cleanup/CleanupSuggestionsEngine.kt`
- Create: `app/src/test/java/com/kd/anddirstat/cleanup/CleanupSuggestionsEngineTest.kt`
- Create: `app/src/main/java/com/kd/anddirstat/ui/screens/cleaners/CleanupSuggestionsScreen.kt`
- Modify: `app/src/main/java/com/kd/anddirstat/model/Models.kt:12-30`
- Modify: `app/src/main/java/com/kd/anddirstat/ui/screens/DiscoverScreen.kt:30-110`
- Modify: `app/src/main/java/com/kd/anddirstat/MainActivity.kt:635-655`

**Interfaces:**
- Produces: `CleanupSuggestionsEngine.generateReport(rootNode: CompactNode, context: Context?): CleanupSuggestionsReport`
- Produces: `CleanupSuggestionsScreen(report: CleanupSuggestionsReport, onStageGroup: (CleanupSuggestionGroup) -> Unit, onStageItem: (StagedQueueItem) -> Unit, onOpenQueue: () -> Unit, onBack: () -> Unit)`

- [ ] **Step 1: Write unit tests for `CleanupSuggestionsEngine`**

```kotlin
// app/src/test/java/com/kd/anddirstat/cleanup/CleanupSuggestionsEngineTest.kt
package com.kd.anddirstat.cleanup

import com.kd.anddirstat.model.CompactNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupSuggestionsEngineTest {

    @Test
    fun testFindsCachesAndTempFiles() {
        val root = CompactNode("root", true, 0L, arrayOf(
            CompactNode("cache", true, 5000000L, emptyArray()),
            CompactNode("debug.log", false, 2000000L),
            CompactNode("document.pdf", false, 10000000L)
        ))

        val report = CleanupSuggestionsEngine.generateReport(root, null)
        assertTrue(report.totalReclaimableBytes >= 7000000L)
        val cacheGroup = report.groups.find { it.id == "caches_and_temp" }
        assertTrue(cacheGroup != null && cacheGroup.items.size >= 2)
    }

    @Test
    fun testFindsObsoleteApks() {
        val downloads = CompactNode("Download", true, 15000000L, arrayOf(
            CompactNode("app_release.apk", false, 15000000L)
        ))
        val root = CompactNode("root", true, 15000000L, arrayOf(downloads))

        val report = CleanupSuggestionsEngine.generateReport(root, null)
        val apkGroup = report.groups.find { it.id == "obsolete_apks" }
        assertTrue(apkGroup != null && apkGroup.totalBytes == 15000000L)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests com.kd.anddirstat.cleanup.CleanupSuggestionsEngineTest`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement `CleanupSuggestionsEngine.kt`**

```kotlin
// app/src/main/java/com/kd/anddirstat/cleanup/CleanupSuggestionsEngine.kt
package com.kd.anddirstat.cleanup

import android.content.Context
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.util.FileUtils

object CleanupSuggestionsEngine {

    fun generateReport(rootNode: CompactNode, context: Context?): CleanupSuggestionsReport {
        val cacheItems = mutableListOf<StagedQueueItem>()
        val apkItems = mutableListOf<StagedQueueItem>()
        val trashItems = mutableListOf<StagedQueueItem>()
        val tempItems = mutableListOf<StagedQueueItem>()

        fun traverse(node: CompactNode, currentPath: String) {
            val name = node.name
            if (name == "[Free Space]" || name == "[System & OS]") return

            val path = if (currentPath.isEmpty()) name else "$currentPath/$name"

            if (SemanticClassifier.isReclaimable(node, path)) {
                val category = SemanticClassifier.classify(node, path)
                val item = StagedQueueItem(
                    node = node,
                    path = path,
                    reclaimableBytes = node.size,
                    category = category,
                    isApp = false,
                    pkgName = null
                )
                when (category) {
                    SemanticCategory.TRASH -> trashItems.add(item)
                    SemanticCategory.OBSOLETE_APK -> apkItems.add(item)
                    SemanticCategory.CACHE_TEMP -> {
                        if (node.name.endsWith(".tmp", true) || node.name.endsWith(".log", true)) {
                            tempItems.add(item)
                        } else {
                            cacheItems.add(item)
                        }
                    }
                    else -> tempItems.add(item)
                }
                // Do not recurse into already marked directories to avoid duplicate size counting
                if (node.isDirectory) return
            }

            node.children?.forEach { traverse(it, path) }
        }

        traverse(rootNode, "")

        // Also check duplicate candidate items using existing helper
        val duplicateEntries = StorageFilterHelper.findDuplicates(rootNode, limit = 50)
        val duplicateItems = duplicateEntries.map {
            StagedQueueItem(
                node = it.node,
                path = it.path,
                reclaimableBytes = it.node.size,
                category = SemanticCategory.OTHER,
                isApp = false,
                pkgName = null
            )
        }

        val groups = mutableListOf<CleanupSuggestionGroup>()

        if (cacheItems.isNotEmpty()) {
            groups.add(
                CleanupSuggestionGroup(
                    id = "caches",
                    title = "App & System Caches",
                    description = "Temporary cache data safe to reclaim",
                    icon = "cached",
                    totalBytes = cacheItems.sumOf { it.reclaimableBytes },
                    items = cacheItems.sortedByDescending { it.reclaimableBytes }
                )
            )
        }

        if (apkItems.isNotEmpty()) {
            groups.add(
                CleanupSuggestionGroup(
                    id = "obsolete_apks",
                    title = "Downloaded APK Installers",
                    description = "Package installation files left in Downloads",
                    icon = "android",
                    totalBytes = apkItems.sumOf { it.reclaimableBytes },
                    items = apkItems.sortedByDescending { it.reclaimableBytes }
                )
            )
        }

        if (trashItems.isNotEmpty()) {
            groups.add(
                CleanupSuggestionGroup(
                    id = "recycle_bin",
                    title = "Recycle Bin",
                    description = "Items currently awaiting permanent removal",
                    icon = "delete",
                    totalBytes = trashItems.sumOf { it.reclaimableBytes },
                    items = trashItems.sortedByDescending { it.reclaimableBytes }
                )
            )
        }

        if (tempItems.isNotEmpty()) {
            groups.add(
                CleanupSuggestionGroup(
                    id = "temp_files",
                    title = "Temporary & Log Files",
                    description = "Residual log and partial download files",
                    icon = "draft",
                    totalBytes = tempItems.sumOf { it.reclaimableBytes },
                    items = tempItems.sortedByDescending { it.reclaimableBytes }
                )
            )
        }

        if (duplicateItems.isNotEmpty()) {
            groups.add(
                CleanupSuggestionGroup(
                    id = "duplicates",
                    title = "Duplicate Files",
                    description = "Identical files consuming redundant storage",
                    icon = "content_copy",
                    totalBytes = duplicateItems.sumOf { it.reclaimableBytes },
                    items = duplicateItems
                )
            )
        }

        val totalReclaimable = groups.sumOf { it.totalBytes }
        val totalCount = groups.sumOf { it.items.size }

        return CleanupSuggestionsReport(
            totalReclaimableBytes = totalReclaimable,
            totalItemCount = totalCount,
            groups = groups
        )
    }
}
```

- [ ] **Step 4: Create `CleanupSuggestionsScreen.kt` and integrate with `DiscoverScreen.kt`**

- In `model/Models.kt`: Add `const val CLEANUP_SUGGESTIONS = "cleanup_suggestions"`.
- Create `CleanupSuggestionsScreen.kt` using `CleanerScaffold`:
  - Shows top banner with total safe reclaimable space and "Stage All Safe Items" button.
  - Lists each `CleanupSuggestionGroup` card with category icon, title, description, size badge, and a "Stage Group" button.
  - Tapping expands the group to view and stage individual candidate files.
- In `DiscoverScreen.kt`:
  - Add a "Smart Cleanup Recommendations" card with reclaimable space summary and a "View Recommendations" action leading to `AppDestinations.CLEANUP_SUGGESTIONS`.
- In `MainActivity.kt`: Register `composable(AppDestinations.CLEANUP_SUGGESTIONS)` in the `NavHost`.

- [ ] **Step 5: Run tests and verify**

Run: `./gradlew testDebugUnitTest --tests com.kd.anddirstat.cleanup.CleanupSuggestionsEngineTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kd/anddirstat/cleanup/CleanupSuggestionsEngine.kt app/src/test/java/com/kd/anddirstat/cleanup/CleanupSuggestionsEngineTest.kt app/src/main/java/com/kd/anddirstat/ui/screens/cleaners/CleanupSuggestionsScreen.kt app/src/main/java/com/kd/anddirstat/model/Models.kt app/src/main/java/com/kd/anddirstat/ui/screens/DiscoverScreen.kt app/src/main/java/com/kd/anddirstat/MainActivity.kt
git commit -m "feat(cleanup): add cleanup suggestions engine and smart recommendations screen"
```

---

### Task 5: End-to-End Verification & Integration Polish

**Files:**
- Modify: `app/src/main/java/com/kd/anddirstat/MainActivity.kt`
- Modify: `app/src/main/java/com/kd/anddirstat/treemap/TreemapCanvas.kt`

- [ ] **Step 1: Run all unit test suites across the project**

Run: `./gradlew testDebugUnitTest`
Expected: 100% test pass rate with zero test failures.

- [ ] **Step 2: Verify release and debug compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL without warnings or Kotlin compiler errors.

- [ ] **Step 3: Verification of constraints**
- Verify Treemap 60fps rendering: hatching pattern runs with no allocation in inner render loop.
- Verify safe boundaries: root mounts and Android critical paths cannot be staged or deleted.
- Verify UI consistency: dark/light theme, amoled black, and dynamic colors apply seamlessly.

- [ ] **Step 4: Final commit and summary**

```bash
git commit -m "feat(cleanup): complete disktree-inspired disk cleanup improvements"
```
