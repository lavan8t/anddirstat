package com.kd.anddirstat.model

import androidx.compose.ui.graphics.Color

class CompactNode(
    val name: String,
    val isDirectory: Boolean,
    var size: Long = 0L,
    var children: Array<CompactNode>? = null
)

object AppDestinations {
    const val TREE = "tree"
    const val EXPLORER = "explorer"
    const val TYPES = "types"
    const val DISCOVER = "discover"

    // Sub-destinations
    const val SETTINGS = "settings"
    const val CLEANER_DUPLICATES = "cleaner_duplicates"
    const val CLEANER_EMPTY_FOLDERS = "cleaner_empty_folders"
    const val CLEANER_SCREENSHOTS = "cleaner_screenshots"
    const val CLEANER_RECYCLE_BIN = "cleaner_recycle_bin"
    const val STARRED = "starred_files"
    const val LARGEST_FILES = "largest_files"

    val TOP_LEVEL_DESTINATIONS = setOf(TREE, EXPLORER, TYPES, DISCOVER)

    fun isTopLevel(route: String?): Boolean = route in TOP_LEVEL_DESTINATIONS
}

data class ExtensionStat(
    val extension: String,
    val totalSize: Long,
    val count: Int,
    val color: Color,
    val category: String
)

data class NavEntry(
    val node: CompactNode,
    val path: String
)

typealias TopFileEntry = NavEntry
