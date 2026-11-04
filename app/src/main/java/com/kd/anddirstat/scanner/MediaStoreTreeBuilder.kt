package com.kd.anddirstat.scanner

import com.kd.anddirstat.model.CompactNode

data class MediaStoreEntry(
    val path: String,
    val size: Long,
    val isTrashed: Boolean = false,
    val isDirectory: Boolean = false
)

object MediaStoreTreeBuilder {

    private class MutableDirNode(
        val name: String,
        var isDirectory: Boolean,
        var size: Long = 0L,
        val children: MutableMap<String, MutableDirNode> = mutableMapOf()
    ) {
        fun toCompactNode(): CompactNode {
            val childNodes = if (isDirectory && children.isNotEmpty()) {
                children.values.map { it.toCompactNode() }.toTypedArray()
            } else null

            return CompactNode(
                name = name,
                isDirectory = isDirectory,
                size = size,
                children = childNodes
            )
        }
    }

    fun buildTree(
        rootPath: String,
        entries: Sequence<MediaStoreEntry>
    ): CompactNode {
        val normalizedRoot = rootPath.trimEnd('/')
        val rootNode = MutableDirNode(
            name = if (normalizedRoot.isEmpty()) "/" else normalizedRoot,
            isDirectory = true
        )

        for (entry in entries) {
            val cleanPath = entry.path.trimEnd('/')
            if (!cleanPath.startsWith(normalizedRoot)) continue

            val relative = cleanPath.removePrefix(normalizedRoot).removePrefix("/")
            if (relative.isEmpty()) continue

            val segments = relative.split('/')
            var current = rootNode
            current.size += entry.size

            for (i in 0 until segments.size - 1) {
                val seg = segments[i]
                val dir = current.children.getOrPut(seg) {
                    MutableDirNode(name = seg, isDirectory = true)
                }
                dir.size += entry.size
                current = dir
            }

            val leafName = segments.last()
            val leaf = current.children.getOrPut(leafName) {
                MutableDirNode(
                    name = leafName,
                    isDirectory = entry.isDirectory,
                    size = entry.size
                )
            }
            if (leaf !== current && leaf.size == 0L) {
                leaf.size = entry.size
            }
        }

        return rootNode.toCompactNode()
    }
}
