package com.kd.anddirstat.viewmodel

import java.io.File

data class DeletionResult(val deleted: Boolean, val blockReason: String? = null)

data class OperationProgress(val current: Int = 0, val total: Int = 0, val fileName: String = "")

/** Routes every cleaner commit through the guarded engine via a plain Android adapter. */
interface CleanerDeleter {
    suspend fun deleteFiles(
        paths: Set<String>,
        isPermanent: Boolean,
        actionName: String,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit
    ): Int

    suspend fun deleteFile(path: String, isPermanent: Boolean): DeletionResult
}

interface CleanerNotifier {
    fun notify(message: String)
}

enum class HapticKind { LONG_PRESS, TEXT_MOVE, DELETE, KEEP }

interface CleanerHaptics {
    fun perform(kind: HapticKind)
}

interface StarRepository {
    fun starredPaths(): Set<String>
    fun isStarred(path: String): Boolean
    fun toggle(path: String)
}

interface KeptScreenshotsStore {
    fun load(): Set<String>
    fun save(paths: Set<String>)
}

interface ScreenshotSource {
    suspend fun load(keptPaths: Set<String>): List<ScreenshotItem>
}

interface DuplicateSource {
    fun load(): List<DuplicateFileItem>
}

interface TrashSource {
    fun load(): List<TrashedItem>
}

interface EmptyFolderSource {
    fun load(): List<File>
}

interface TrashRestorer {
    suspend fun restore(targets: List<Pair<String, String>>, onProgress: (Int, Int, String) -> Unit): Int
}

interface FileOpener {
    fun open(path: String)
}

interface StarredSource {
    fun load(): List<StarredItem>
}
