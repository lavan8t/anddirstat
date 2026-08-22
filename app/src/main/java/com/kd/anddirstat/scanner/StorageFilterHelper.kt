package com.kd.anddirstat.scanner

import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.ExtensionStat
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.treemap.getNodeColor

object StorageFilterHelper {

    fun filterStorageTree(
        rawRoot: CompactNode?,
        showFreeSpace: Boolean,
        showSystemApps: Boolean,
        deviceTotalBytes: Long
    ): CompactNode? {
        if (rawRoot == null) return null
        val rawChildren = rawRoot.children ?: return rawRoot

        val newChildren = mutableListOf<CompactNode>()
        for (child in rawChildren) {
            when {
                child.name == "[Free Space]" -> {
                    if (showFreeSpace) {
                        newChildren.add(child)
                    }
                }
                child.name == "Apps & System Packages" -> {
                    val appChildren = child.children
                    val filteredApps = if (!showSystemApps && appChildren != null) {
                        appChildren.filter { !it.name.endsWith(" (System)") }.toTypedArray()
                    } else {
                        appChildren
                    }
                    val appsSize = filteredApps?.sumOf { it.size } ?: 0L
                    if (filteredApps != null && filteredApps.isNotEmpty() && appsSize > 0L) {
                        newChildren.add(
                            CompactNode(
                                name = "Apps & System Packages",
                                isDirectory = true,
                                size = appsSize,
                                children = filteredApps
                            )
                        )
                    }
                }
                else -> {
                    newChildren.add(child)
                }
            }
        }

        val totalSize = if (showFreeSpace && deviceTotalBytes > 0L) {
            deviceTotalBytes
        } else {
            newChildren.sumOf { it.size }
        }

        return CompactNode(
            name = rawRoot.name,
            isDirectory = true,
            size = totalSize,
            children = newChildren.toTypedArray()
        )
    }

    fun aggregateExtensionStats(rootNode: CompactNode): List<ExtensionStat> {
        val statsMap = mutableMapOf<String, Pair<Long, Int>>()

        fun collect(node: CompactNode) {
            val name = node.name
            if (name == "[Free Space]" || name == "[System & OS]") {
                val cur = statsMap[name] ?: Pair(0L, 0)
                statsMap[name] = Pair(cur.first + node.size, cur.second + 1)
                return
            }
            if (!node.isDirectory) {
                val ext = if (name.startsWith("App Code") || name.startsWith("APK (") || name.endsWith(".apk", ignoreCase = true)) {
                    ".apk"
                } else if (name == "Cache" || name == "App Cache") {
                    "Cache"
                } else if (name == "Data" || name == "App Data") {
                    "Data"
                } else {
                    val e = name.substringAfterLast('.', "").lowercase()
                    if (e.isEmpty()) "[no ext]" else ".$e"
                }
                val cur = statsMap[ext] ?: Pair(0L, 0)
                statsMap[ext] = Pair(cur.first + node.size, cur.second + 1)
            } else {
                node.children?.forEach { collect(it) }
            }
        }

        collect(rootNode)

        return statsMap.map { (ext, pair) ->
            val dummyNode = CompactNode(name = if (ext.startsWith(".")) "file$ext" else ext, isDirectory = false, size = pair.first)
            val color = getNodeColor(dummyNode)
            val category = when {
                ext == "[Free Space]" -> "Free Storage"
                ext == "[System & OS]" -> "System / Reserved"
                ext == "Cache" -> "App Cache"
                ext == "Data" -> "App Data"
                ext in listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".flv", ".3gp", ".ts", ".wmv", ".m4v") -> "Video"
                ext in listOf(".mp3", ".flac", ".wav", ".m4a", ".ogg", ".aac", ".opus", ".wma", ".mid") -> "Audio"
                ext in listOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".raw", ".svg", ".gif", ".bmp", ".ico") -> "Image"
                ext in listOf(".apk", ".apks", ".xapk", ".apkm", ".obb", ".aab") -> "App Package"
                ext in listOf(".pdf", ".doc", ".docx", ".txt", ".xlsx", ".xls", ".ppt", ".pptx", ".csv", ".epub") -> "Document"
                ext in listOf(".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz", ".iso") -> "Archive"
                else -> "Other"
            }
            ExtensionStat(
                extension = ext,
                totalSize = pair.first,
                count = pair.second,
                color = color,
                category = category
            )
        }.sortedByDescending { it.totalSize }
    }

    fun aggregateTopFiles(rootNode: CompactNode, limit: Int = 30): List<TopFileEntry> {
        val list = mutableListOf<TopFileEntry>()
        fun collect(node: CompactNode, currentPath: String) {
            if (node.name == "[Free Space]" || node.name == "[System & OS]") return
            if (!node.isDirectory) {
                list.add(TopFileEntry(node, currentPath))
            } else {
                node.children?.forEach { child ->
                    val childPath = if (currentPath == "Device Storage") child.name else "$currentPath/${child.name}"
                    collect(child, childPath)
                }
            }
        }
        collect(rootNode, rootNode.name)
        return list.sortedByDescending { it.node.size }.take(limit)
    }



    fun filterByPreset(rootNode: CompactNode, preset: String): List<TopFileEntry> {
        val allFiles = aggregateTopFiles(rootNode, limit = 500)
        return when (preset) {
            "> 1 GB" -> allFiles.filter { it.node.size >= 1024L * 1024L * 1024L }
            "Duplicates" -> {
                val grouped = allFiles.groupBy { "${it.node.name}_${it.node.size}" }
                grouped.filter { it.value.size > 1 }.values.flatten()
            }
            "Old Downloads" -> allFiles.filter { it.path.contains("Download", ignoreCase = true) }
            "APKs" -> allFiles.filter {
                val n = it.node.name.lowercase()
                n.endsWith(".apk") || n.endsWith(".xapk") || n.endsWith(".apks") || n.endsWith(".apkm") || n.startsWith("app code")
            }
            else -> allFiles
        }
    }

    fun searchTree(rootNode: CompactNode, rawQuery: String): List<TopFileEntry> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()

        val allFiles = aggregateTopFiles(rootNode, limit = 1000)

        // Check size threshold query: e.g. "> 100MB", "> 1GB", "> 500M"
        if (query.startsWith(">") || query.startsWith("<")) {
            val isGreater = query.startsWith(">")
            val numStr = query.substring(1).trim().lowercase()
            val multiplier = when {
                numStr.endsWith("gb") || numStr.endsWith("g") -> 1024L * 1024L * 1024L
                numStr.endsWith("mb") || numStr.endsWith("m") -> 1024L * 1024L
                numStr.endsWith("kb") || numStr.endsWith("k") -> 1024L
                else -> 1L
            }
            val numPart = numStr.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
            val targetBytes = (numPart * multiplier).toLong()
            return if (isGreater) {
                allFiles.filter { it.node.size >= targetBytes }
            } else {
                allFiles.filter { it.node.size <= targetBytes }
            }
        }

        // Extension or name query
        val cleanQuery = query.removePrefix(".").lowercase()
        return allFiles.filter {
            it.node.name.contains(cleanQuery, ignoreCase = true) ||
            it.path.contains(cleanQuery, ignoreCase = true)
        }
    }
}
