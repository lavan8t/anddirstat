package com.kd.anddirstat.model

import androidx.compose.ui.graphics.Color

class CompactNode(
    val name: String,
    val isDirectory: Boolean,
    var size: Long = 0L,
    var children: Array<CompactNode>? = null
)

object AppDestinations {
    const val MAP = "map"
    const val EXPLORER = "explorer"
    const val TYPES = "types"
    const val DISCOVER = "discover"
    const val SETTINGS = "settings"
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

data class TopFileEntry(
    val node: CompactNode,
    val path: String
)
