package com.kd.anddirstat.scanner

import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.ExtensionStat
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.treemap.getNodeColor
import com.kd.anddirstat.util.FileUtils

private val VIDEO_EXTENSIONS = setOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".flv", ".3gp", ".ts", ".wmv", ".m4v")
private val AUDIO_EXTENSIONS = setOf(".mp3", ".flac", ".wav", ".m4a", ".ogg", ".aac", ".opus", ".wma", ".mid")
private val IMAGE_EXTENSIONS = setOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".raw", ".svg", ".gif", ".bmp", ".ico")
private val PACKAGE_EXTENSIONS = setOf(".apk", ".apks", ".xapk", ".apkm", ".obb", ".aab")
private val DOCUMENT_EXTENSIONS = setOf(".pdf", ".doc", ".docx", ".txt", ".xlsx", ".xls", ".ppt", ".pptx", ".csv", ".epub")
private val ARCHIVE_EXTENSIONS = setOf(".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz", ".iso")

private const val RECYCLE_BIN_MARKER = "[Recycle Bin]"

private class DuplicateCandidate(val node: CompactNode, private val parentPath: String) {
    private var cachedPath: String? = null

    fun path(): String {
        var known = cachedPath
        if (known == null) {
            known = if (parentPath.isEmpty()) node.name else "$parentPath/${node.name}"
            cachedPath = known
        }
        return known
    }

    fun toEntry(): TopFileEntry = TopFileEntry(node, path())
}

// Replaces the "size_ext" / "name_size" string keys. A Long always renders as digits only, so the
// split point is unambiguous and distinct (size, text) pairs can never collide the way strings could.
private data class DuplicateKey(val size: Long, val text: String)

private fun appendDuplicateGroups(
    groups: Map<DuplicateKey, List<DuplicateCandidate>>,
    out: MutableList<TopFileEntry>,
    seen: MutableSet<String>
) {
    for (group in groups.values) {
        if (group.size > 1) {
            for (candidate in group) {
                if (seen.add(candidate.path())) out.add(candidate.toEntry())
            }
        }
    }
}

// Identical output to s.filter { it.isDigit() || it == '.' }, but skips the StringBuilder when the
// rejected characters are only a trailing unit suffix, which is the usual "> 100MB" shape.
private fun numericChars(s: String): String {
    var firstRejected = -1
    for (i in s.indices) {
        val c = s[i]
        if (!c.isDigit() && c != '.') {
            firstRejected = i
            break
        }
    }
    if (firstRejected < 0) return s
    for (i in (firstRejected + 1) until s.length) {
        val c = s[i]
        if (c.isDigit() || c == '.') return s.filter { it.isDigit() || it == '.' }
    }
    return s.substring(0, firstRejected)
}

object StorageFilterHelper {

    fun filterStorageTree(
        rawRoot: CompactNode?,
        showFreeSpace: Boolean,
        showSystemApps: Boolean,
        showHiddenFiles: Boolean = true,
        showSystemOS: Boolean = true,
        deviceTotalBytes: Long
    ): CompactNode? {
        if (rawRoot == null) return null

        val trashedItems = mutableListOf<CompactNode>()

        fun processNode(node: CompactNode): CompactNode? {
            val name = node.name
            val isTrash = name.startsWith(".trashed") || name.equals(".trash", ignoreCase = true) || name.equals(".recycle", ignoreCase = true)
            if (isTrash) {
                trashedItems.add(node)
                return null
            }
            if (!showHiddenFiles && name.startsWith(".")) {
                return null
            }
            val children = node.children ?: return node
            if (!node.isDirectory || children.isEmpty()) return node

            var anyChanged = false
            val filteredKids = ArrayList<CompactNode>(children.size)
            var newSize = 0L
            for (child in children) {
                val processed = processNode(child)
                if (processed == null) {
                    anyChanged = true
                } else {
                    if (processed !== child) anyChanged = true
                    filteredKids.add(processed)
                    newSize += processed.size
                }
            }

            if (!anyChanged && filteredKids.size == children.size) {
                return node
            }

            return CompactNode(
                name = node.name,
                isDirectory = true,
                size = if (newSize > 0L) newSize else node.size,
                children = filteredKids.toTypedArray()
            )
        }

        val rawChildren = rawRoot.children ?: return rawRoot

        val newChildren = mutableListOf<CompactNode>()
        for (rawChild in rawChildren) {
            val child = processNode(rawChild)
            if (child == null) continue
            when {
                child.name == "[Free Space]" -> {
                    if (showFreeSpace) {
                        newChildren.add(child)
                    }
                }
                child.name == "[System & OS]" || child.name == "System & OS" ||
                child.name == "[Temporary System Files]" || child.name == "Temporary System Files" -> {
                    if (showSystemOS) {
                        newChildren.add(child)
                    }
                }
                child.name == "Apps & System Packages" -> {
                    val appChildren = child.children
                    val filteredApps = if (!showSystemApps && appChildren != null) {
                        appChildren.filter { !FileUtils.AppPackageRegistry.isSystemApp(it.name) }.toTypedArray()
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
            if (name == "[Free Space]" || name == "[System & OS]" ||
                name == "[Temporary System Files]" || name == "Temporary System Files" ||
                name == "Cache" || name == "App Cache" || name == "Data" || name == "App Data" ||
                name.startsWith("App Code") || name == "Apps & System Packages" ||
                node.children?.any { it.name.startsWith("App Code") } == true) {
                return
            }
            if (name == "[Recycle Bin]" || name.startsWith(".trashed")) {
                val cur = statsMap["[trashed]"] ?: Pair(0L, 0)
                statsMap["[trashed]"] = Pair(cur.first + node.size, cur.second + (node.children?.size ?: 1))
                return
            }
            if (!node.isDirectory) {
                val ext = if (name.endsWith(".apk", ignoreCase = true) || name.endsWith(".apks", ignoreCase = true) || name.endsWith(".xapk", ignoreCase = true) || name.endsWith(".obb", ignoreCase = true)) {
                    ".apk"
                } else {
                    val e = name.substringAfterLast('.', "").lowercase()
                    if (e.isEmpty() || e == name.lowercase()) "[no ext]" else ".$e"
                }
                val cur = statsMap[ext] ?: Pair(0L, 0)
                statsMap[ext] = Pair(cur.first + node.size, cur.second + 1)
            } else {
                node.children?.forEach { collect(it) }
            }
        }

        collect(rootNode)

        return statsMap.map { (ext, pair) ->
            // getNodeColor() keys off the name and size only, so one throwaway node per statistic
            // is cheaper than duplicating its colour table.
            val dummyNode = CompactNode(name = if (ext.startsWith(".")) "file$ext" else ext, isDirectory = false, size = pair.first)
            val color = if (ext == "[trashed]") androidx.compose.ui.graphics.Color(0xFFF43F5E) else getNodeColor(dummyNode)
            val category = when {
                ext == "[Free Space]" || ext == "Free Space" -> "Free Space"
                ext == "[System & OS]" || ext == "System & OS" || ext == "[Temporary System Files]" || ext == "Temporary System Files" -> "System & OS"
                ext == "[trashed]" || ext == "[Recycle Bin]" || ext == ".trashed" || ext == "Recycle Bin" -> "Recycle Bin"
                ext == "Cache" -> "App Cache"
                ext == "Data" -> "App Data"
                ext in VIDEO_EXTENSIONS -> "Video"
                ext in AUDIO_EXTENSIONS -> "Audio"
                ext in IMAGE_EXTENSIONS -> "Image"
                ext in PACKAGE_EXTENSIONS -> "App Package"
                ext in DOCUMENT_EXTENSIONS -> "Document"
                ext in ARCHIVE_EXTENSIONS -> "Archive"
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
        val minHeap = java.util.PriorityQueue<TopFileEntry>(limit + 1, compareBy { it.node.size })

        // Prune round: non-internal-storage elements (Free space, System OS, Recycle Bin, the Apps
        // container) plus the synthetic app split nodes. Same predicates and same case sensitivity as
        // the original chains; cheapest round, so it runs before any lowercase copy is built.
        fun isPrunedName(name: String): Boolean =
            name == "[Free Space]" || name == "[System & OS]" || name == "[Recycle Bin]" ||
                name.equals("System & OS", ignoreCase = true) || name.equals("[System & OS]", ignoreCase = true) ||
                name == "Apps & System Packages" || name.startsWith(".trashed") ||
                name.startsWith("App Code") || name.startsWith("APK (") ||
                name.startsWith("Data (") || name.startsWith("Cache (") ||
                name.startsWith("External Data (") || name.startsWith("External Media (")

        fun isPrunedPath(path: String): Boolean =
            path.startsWith("Apps & System Packages") || path.contains("/Apps & System Packages") ||
                path.contains("Android/data") || path.contains("Android/obb")

        // Filter round: temporary and system runtime files. These always ran against lowercased
        // copies, so keep them: lowercase() returns the same instance when nothing changes, and
        // contains(ignoreCase = true) is a per-character loop that costs roughly twice the whole walk.
        fun isFilteredName(name: String): Boolean {
            val lowerName = name.lowercase()
            return lowerName.endsWith(".tmp") || lowerName.endsWith(".temp") ||
                lowerName.endsWith(".log") || lowerName.endsWith(".crdownload") ||
                lowerName.endsWith(".part") || lowerName.endsWith(".cache") ||
                lowerName.endsWith(".dmp") || lowerName.endsWith(".dump") ||
                lowerName.endsWith(".trace") || lowerName == ".nomedia" ||
                lowerName.startsWith(".pending") || lowerName.startsWith(".tmp") ||
                lowerName == "thumbs.db" || lowerName == ".ds_store"
        }

        fun isFilteredPath(path: String): Boolean {
            val lowerPath = path.lowercase()
            return lowerPath.contains("/.thumbnails") || lowerPath.contains("/cache/") ||
                lowerPath.contains("/.cache/") || lowerPath.contains("/temp/") ||
                lowerPath.contains("/tmp/")
        }

        fun collectChildren(node: CompactNode, currentPath: String) {
            if (!node.isDirectory) {
                if (minHeap.size < limit) {
                    minHeap.offer(TopFileEntry(node, currentPath))
                } else if (node.size > minHeap.peek()!!.node.size) {
                    minHeap.poll()
                    minHeap.offer(TopFileEntry(node, currentPath))
                }
                return
            }
            val children = node.children ?: return
            for (index in children.indices) {
                val child = children[index]
                val childName = child.name
                // Name-rejected children are dropped before their path String is built.
                if (isPrunedName(childName)) continue
                val childPath = if (currentPath == "Device Storage") childName else "$currentPath/$childName"
                if (isPrunedPath(childPath)) continue
                if (isFilteredName(childName) || isFilteredPath(childPath)) continue
                collectChildren(child, childPath)
            }
        }

        val rootPath = rootNode.name
        if (!isPrunedName(rootPath) && !isPrunedPath(rootPath) &&
            !isFilteredName(rootPath) && !isFilteredPath(rootPath)
        ) {
            collectChildren(rootNode, rootPath)
        }
        val result = ArrayList<TopFileEntry>(minHeap.size)
        while (minHeap.isNotEmpty()) {
            result.add(minHeap.poll()!!)
        }
        result.reverse()
        return result
    }



    fun getScreenshots(rootNode: CompactNode, limit: Int = 100): List<TopFileEntry> {
        val result = mutableListOf<TopFileEntry>()
        fun traverse(node: CompactNode, currentPath: String) {
            val name = node.name
            if (name == "[Free Space]" || name == "[System & OS]" || name == "[Recycle Bin]" ||
                name == "Apps & System Packages" || name.startsWith(".trashed")) return

            val path = if (currentPath == "Device Storage") name else "$currentPath/$name"
            if (!node.isDirectory) {
                val isImage = name.endsWith(".png", ignoreCase = true) || name.endsWith(".jpg", ignoreCase = true) ||
                        name.endsWith(".jpeg", ignoreCase = true) || name.endsWith(".webp", ignoreCase = true)
                val isScreenshot = isImage && (
                        path.contains("screenshot", ignoreCase = true) ||
                        path.contains("screen_shot", ignoreCase = true) ||
                        name.startsWith("Screenshot", ignoreCase = true) ||
                        name.startsWith("Screen_Shot", ignoreCase = true) ||
                        name.contains("screenshot", ignoreCase = true)
                )
                if (isScreenshot) {
                    result.add(TopFileEntry(node, path))
                }
            } else {
                node.children?.forEach { traverse(it, path) }
            }
        }
        traverse(rootNode, rootNode.name)
        return result.sortedByDescending { it.node.size }.take(limit)
    }

    fun findDuplicates(rootNode: CompactNode, limit: Int = 200): List<TopFileEntry> {
        val candidates = ArrayList<DuplicateCandidate>()

        // The marker contains no '/', so it can never straddle the "/" that join() inserts: testing
        // the parent path and the node name separately matches searching the joined path, and the
        // parent half is shared by every sibling.
        fun traverse(node: CompactNode, parentPath: String, parentHasRecycleBin: Boolean) {
            val name = node.name
            if (!node.isDirectory) {
                if (node.size > 0L && !name.startsWith(".trashed") &&
                    !parentHasRecycleBin && !name.contains(RECYCLE_BIN_MARKER)
                ) {
                    candidates.add(DuplicateCandidate(node, parentPath))
                }
                return
            }
            val children = node.children ?: return
            val path = if (parentPath.isEmpty()) name else "$parentPath/$name"
            val childParentHasRecycleBin = parentHasRecycleBin || name.contains(RECYCLE_BIN_MARKER)
            for (index in children.indices) {
                traverse(children[index], path, childParentHasRecycleBin)
            }
        }
        traverse(rootNode, "", false)

        val duplicateList = mutableListOf<TopFileEntry>()
        val seen = mutableSetOf<String>()

        // 1. Match files by exact byte size (>= 10KB) with same extension
        val bySizeAndExt = linkedMapOf<DuplicateKey, MutableList<DuplicateCandidate>>()
        for (index in candidates.indices) {
            val candidate = candidates[index]
            val node = candidate.node
            if (node.size < 10240L) continue
            val key = DuplicateKey(node.size, node.name.substringAfterLast('.', "").lowercase())
            bySizeAndExt.getOrPut(key) { mutableListOf() }.add(candidate)
        }
        appendDuplicateGroups(bySizeAndExt, duplicateList, seen)

        // 2. Match files by exact name and size for every file, including those matched above
        val byExactNameAndSize = linkedMapOf<DuplicateKey, MutableList<DuplicateCandidate>>()
        for (candidate in candidates) {
            val node = candidate.node
            val key = DuplicateKey(node.size, node.name.lowercase())
            byExactNameAndSize.getOrPut(key) { mutableListOf() }.add(candidate)
        }
        appendDuplicateGroups(byExactNameAndSize, duplicateList, seen)

        return duplicateList.sortedByDescending { it.node.size }.take(limit)
    }

    fun filterByPreset(rootNode: CompactNode, preset: String, context: android.content.Context? = null): List<TopFileEntry> {
        // Only the branches below read it; "Screenshots" and "Duplicates" walk the tree themselves.
        fun topFiles() = aggregateTopFiles(rootNode, limit = 500)
        return when (preset) {
            "Starred" -> if (context != null) {
                val allFiles = topFiles()
                val starred = com.kd.anddirstat.util.FavoritesManager.getStarredPaths(context)
                allFiles.filter { starred.contains(it.path) || com.kd.anddirstat.util.FavoritesManager.isStarred(context, it.path) }
            } else emptyList()
            "> 1 GB" -> topFiles().filter { it.node.size >= 1024L * 1024L * 1024L }
            "Screenshots" -> getScreenshots(rootNode)
            "Duplicates" -> findDuplicates(rootNode)
            "Old Downloads" -> topFiles().filter { it.path.contains("Download", ignoreCase = true) }
            "APKs" -> topFiles().filter {
                val n = it.node.name
                n.endsWith(".apk", ignoreCase = true) || n.endsWith(".xapk", ignoreCase = true) ||
                    n.endsWith(".apks", ignoreCase = true) || n.endsWith(".apkm", ignoreCase = true) ||
                    n.startsWith("App Code", ignoreCase = true)
            }
            else -> topFiles()
        }
    }

    fun searchTree(rootNode: CompactNode, rawQuery: String): List<TopFileEntry> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()

        if (query.equals("Duplicates", ignoreCase = true) || query.equals("Duplicate", ignoreCase = true)) {
            return findDuplicates(rootNode)
        }

        val allFiles = aggregateTopFiles(rootNode, limit = 1000)

        // Check size threshold query: e.g. "> 100MB", "> 1GB", "> 500M"
        // Both the threshold branch and the name branch below read allFiles, so the walk stays.
        if (query.startsWith(">") || query.startsWith("<")) {
            val isGreater = query.startsWith(">")
            val numStr = query.substring(1).trim().lowercase()
            val multiplier = when {
                numStr.endsWith("gb") || numStr.endsWith("g") -> 1024L * 1024L * 1024L
                numStr.endsWith("mb") || numStr.endsWith("m") -> 1024L * 1024L
                numStr.endsWith("kb") || numStr.endsWith("k") -> 1024L
                else -> 1L
            }
            val numPart = numericChars(numStr).toDoubleOrNull() ?: 0.0
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

    fun pruneNodes(root: CompactNode, targets: Set<CompactNode>): CompactNode {
        fun prune(node: CompactNode): CompactNode? {
            if (targets.contains(node)) return null
            val kids = node.children ?: return node
            val remaining = kids.mapNotNull { prune(it) }
            val newSize = if (node.isDirectory) remaining.sumOf { it.size } else node.size
            return CompactNode(
                name = node.name,
                isDirectory = node.isDirectory,
                size = newSize,
                children = remaining.toTypedArray()
            )
        }
        return prune(root) ?: CompactNode(root.name, root.isDirectory, 0L, emptyArray())
    }
}
