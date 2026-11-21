package com.kd.anddirstat.viewmodel

import java.io.File

/** Shared by every cleaner sort dropdown; lives in the ViewModel layer so state is JVM-testable. */
interface SortOption {
    val label: String
    val icon: String
}

enum class ScreenshotSort(override val label: String, override val icon: String) : SortOption {
    NEWEST("Newest First", "schedule"),
    OLDEST("Oldest First", "history"),
    LARGEST("Largest First", "folder_zip")
}

data class ScreenshotItem(
    val file: File,
    val name: String,
    val size: Long,
    val path: String
)

data class SwipeHistoryItem(
    val item: ScreenshotItem,
    val wasDeleted: Boolean,
    val originalIndex: Int
)

data class DuplicateFileItem(
    val file: File,
    val path: String,
    val lastModified: Long
)

data class DuplicateGroup(
    val key: String,
    val fileName: String,
    val fileSize: Long,
    val files: List<DuplicateFileItem>
)

enum class DuplicateSort(override val label: String, override val icon: String) : SortOption {
    LARGEST("Largest Size First", "folder_zip"),
    MOST_COPIES("Most Copies First", "content_copy"),
    NAME_AZ("Name (A to Z)", "sort_by_alpha")
}

enum class AutoSelectStrategy {
    KEEP_NEWEST, KEEP_OLDEST, SELECT_ALL, DESELECT_ALL
}

data class TrashedItem(
    val file: File,
    val cleanName: String,
    val rawName: String,
    val size: Long,
    val lastModified: Long,
    val path: String
)

enum class TrashSort(override val label: String, override val icon: String) : SortOption {
    NEWEST("Newest First", "schedule"),
    OLDEST("Oldest First", "history"),
    LARGEST("Largest First", "folder_zip"),
    SMALLEST("Smallest First", "sort"),
    NAME_AZ("Name (A-Z)", "sort_by_alpha")
}

fun cleanTrashName(rawName: String): String {
    var clean = rawName.removePrefix(".trashed-")
    if (clean.contains("-") && clean.substringBefore("-").all { it.isDigit() }) {
        clean = clean.substringAfter("-")
    }
    return clean.ifEmpty { rawName }
}

data class StarredItem(
    val path: String,
    val file: File,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val exists: Boolean
)
