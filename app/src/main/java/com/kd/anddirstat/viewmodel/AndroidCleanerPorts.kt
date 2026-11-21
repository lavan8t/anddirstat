package com.kd.anddirstat.viewmodel

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.scanner.TreeCacheManager
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileMaintenanceEngine
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidCleanerDeleter(private val context: Context) : CleanerDeleter {
    override suspend fun deleteFiles(
        paths: Set<String>,
        isPermanent: Boolean,
        actionName: String,
        onProgress: (Int, Int, String) -> Unit
    ): Int = FileMaintenanceEngine.batchDelete(
        context = context,
        files = paths.map { File(it) },
        isPermanent = isPermanent,
        actionName = actionName,
        onProgress = onProgress
    ).successCount

    override suspend fun deleteFile(path: String, isPermanent: Boolean): DeletionResult {
        val outcome = FileMaintenanceEngine.deleteFileGuarded(context, File(path), isPermanent)
        return DeletionResult(outcome.deleted, outcome.blockReason)
    }
}

class AndroidCleanerNotifier : CleanerNotifier {
    override fun notify(message: String) = AppNotifier.notify(message)
}

class AndroidCleanerHaptics(context: Context, private val haptic: HapticFeedback) : CleanerHaptics {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun perform(kind: HapticKind) {
        when (kind) {
            HapticKind.LONG_PRESS -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            HapticKind.TEXT_MOVE -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            HapticKind.DELETE -> vibrateOrFallback(true)
            HapticKind.KEEP -> vibrateOrFallback(false)
        }
    }

    private fun vibrateOrFallback(isDelete: Boolean) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = if (isDelete) 220 else 90
                    val duration = if (isDelete) 24L else 14L
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(if (isDelete) 24L else 14L)
                }
            } else {
                haptic.performHapticFeedback(
                    if (isDelete) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                )
            }
        } catch (_: Exception) {
        }
    }
}

class AndroidStarRepository(private val context: Context) : StarRepository {
    override fun starredPaths(): Set<String> = FavoritesManager.getStarredPaths(context)
    override fun isStarred(path: String): Boolean = FavoritesManager.isStarred(context, path)
    override fun toggle(path: String) {
        FavoritesManager.toggleStar(context, path)
    }
}

class AndroidKeptScreenshotsStore(context: Context) : KeptScreenshotsStore {
    private val prefs = context.getSharedPreferences("screenshots_cleaner_prefs", Context.MODE_PRIVATE)

    override fun load(): Set<String> = prefs.getStringSet("kept_screenshots", emptySet()) ?: emptySet()

    override fun save(paths: Set<String>) {
        prefs.edit().putStringSet("kept_screenshots", paths).apply()
    }
}

class AndroidScreenshotSource(private val context: Context) : ScreenshotSource {
    override suspend fun load(keptPaths: Set<String>): List<ScreenshotItem> {
        val items = mutableListOf<ScreenshotItem>()
        val root = TreeCacheManager.loadTree(context)
        if (root != null) {
            val list = StorageFilterHelper.getScreenshots(root, limit = 500)
            for (entry in list) {
                val f = FileUtils.resolveActualFile(entry.path) ?: FileUtils.resolveActualFile(entry.node.name)
                if (f != null && f.exists() && f.canRead() && f.absolutePath !in keptPaths) {
                    items.add(ScreenshotItem(file = f, name = entry.node.name, size = entry.node.size, path = entry.path))
                }
            }
        }

        if (items.isEmpty()) {
            val rootDir = Environment.getExternalStorageDirectory()
            val targetDirs = listOf(
                File(rootDir, "Pictures/Screenshots"),
                File(rootDir, "DCIM/Screenshots"),
                File(rootDir, "Pictures/Screen recordings"),
                File(rootDir, "DCIM/Screen recordings"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots")
            )
            for (dir in targetDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEach { f ->
                        val name = f.name.lowercase()
                        if ((name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                    name.endsWith(".webp") || name.endsWith(".heic")) && f.absolutePath !in keptPaths
                        ) {
                            items.add(ScreenshotItem(file = f, name = f.name, size = f.length(), path = f.absolutePath))
                        }
                    }
                }
            }
        }
        return items.distinctBy { it.file.absolutePath }
    }
}

class AndroidDuplicateSource : DuplicateSource {
    override fun load(): List<DuplicateFileItem> {
        val sizeMap = mutableMapOf<Long, MutableList<File>>()
        val root = Environment.getExternalStorageDirectory()

        fun walk(dir: File, depth: Int = 0) {
            if (depth > 8 || !dir.canRead()) return
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.isFile && f.length() > 512 * 1024L && !f.name.startsWith(".")) {
                    sizeMap.getOrPut(f.length()) { mutableListOf() }.add(f)
                } else if (f.isDirectory && !f.name.equals("Android", ignoreCase = true) && !f.name.startsWith(".")) {
                    walk(f, depth + 1)
                }
            }
        }

        if (root != null && root.exists()) walk(root)

        return sizeMap.values.filter { it.size >= 2 }.flatMap { files ->
            files.groupBy { it.name }.filter { it.value.size >= 2 }.flatMap { (_, matching) ->
                matching.map { DuplicateFileItem(file = it, path = it.absolutePath, lastModified = it.lastModified()) }
            }
        }
    }
}

class AndroidTrashSource : TrashSource {
    override fun load(): List<TrashedItem> {
        val found = mutableListOf<TrashedItem>()
        val root = Environment.getExternalStorageDirectory()
        if (root != null && root.exists()) {
            fun walk(dir: File, depth: Int = 0) {
                if (depth > 8 || !dir.canRead()) return
                val list = dir.listFiles() ?: return
                for (f in list) {
                    if (f.name.startsWith(".trashed-") || f.name.equals(".trash", ignoreCase = true) ||
                        f.name.equals(".trashed", ignoreCase = true)
                    ) {
                        found.add(
                            TrashedItem(
                                file = f,
                                cleanName = cleanTrashName(f.name),
                                rawName = f.name,
                                size = if (f.isDirectory) FileUtils.getFolderSize(f) else f.length(),
                                lastModified = f.lastModified(),
                                path = f.absolutePath
                            )
                        )
                    } else if (f.isDirectory && !f.name.equals("Android", ignoreCase = true)) {
                        walk(f, depth + 1)
                    }
                }
            }
            walk(root)
        }
        return found.distinctBy { it.path }
    }
}

class AndroidEmptyFolderSource : EmptyFolderSource {
    override fun load(): List<File> {
        val found = mutableListOf<File>()

        fun isFolderEmpty(dir: File): Boolean {
            if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return false
            val name = dir.name.lowercase()
            if (name == "android" || name.startsWith(".trashed") || name == "system volume information") return false
            val children = dir.listFiles() ?: return false
            if (children.isEmpty()) {
                found.add(dir)
                return true
            }
            var allSubEmpty = true
            for (child in children) {
                if (child.isDirectory) {
                    if (!isFolderEmpty(child)) allSubEmpty = false
                } else {
                    allSubEmpty = false
                }
            }
            if (allSubEmpty) {
                found.add(dir)
                return true
            }
            return false
        }

        val rootStorage = Environment.getExternalStorageDirectory()
        if (rootStorage != null && rootStorage.exists()) {
            val topDirs = rootStorage.listFiles()?.filter { it.isDirectory && it.canRead() } ?: emptyList()
            for (dir in topDirs) {
                val n = dir.name.lowercase()
                if (n != "android" && !n.startsWith(".")) isFolderEmpty(dir)
            }
        }
        return found.distinctBy { it.absolutePath }.sortedBy { it.absolutePath }
    }
}

class AndroidStarredSource(private val context: Context) : StarredSource {
    override fun load(): List<StarredItem> = FavoritesManager.getStarredPaths(context).map { path ->
        val file = File(path)
        StarredItem(
            path = path,
            file = file,
            name = file.name.ifEmpty { path },
            size = if (file.exists()) file.length() else 0L,
            isDirectory = file.isDirectory,
            exists = file.exists()
        )
    }.sortedBy { it.name.lowercase() }
}

class AndroidTrashRestorer(private val context: Context) : TrashRestorer {
    override suspend fun restore(
        targets: List<Pair<String, String>>,
        onProgress: (Int, Int, String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        var restored = 0
        targets.forEachIndexed { index, (path, name) ->
            onProgress(index + 1, targets.size, name)
            try {
                if (FileUtils.restoreTrashedFile(File(path), context)) restored++
            } catch (_: Exception) {
            }
        }
        restored
    }
}

class AndroidFileOpener(private val context: Context) : FileOpener {
    override fun open(path: String) = FileUtils.openFile(context, File(path))
}
