package com.kd.anddirstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import com.kd.anddirstat.ui.screens.discover.DiscoverDeleteConfirmDialog
import com.kd.anddirstat.ui.screens.discover.DiscoverSelectionBar
import com.kd.anddirstat.ui.screens.discover.SearchFilterCategory
import com.kd.anddirstat.ui.screens.discover.discoverLargeItemsSection
import com.kd.anddirstat.ui.screens.discover.discoverSearchResultsSection
import com.kd.anddirstat.ui.screens.discover.discoverToolsSection
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverView(
    rootNode: CompactNode,
    topFiles: List<TopFileEntry>,
    searchQuery: String = "",
    isSearchActive: Boolean = false,
    onSearchQueryChange: ((String) -> Unit)? = null,
    onNodeClick: (CompactNode, String) -> Unit,
    onNodesDeleted: (Set<CompactNode>) -> Unit = {},
    onRefresh: () -> Unit = {},
    onNavigateTo: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var selectedEntries by remember(rootNode, searchQuery) { mutableStateOf(setOf<TopFileEntry>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var isDeleting by remember { mutableStateOf(false) }
    var deleteCurrentCount by remember { mutableIntStateOf(0) }
    var deleteTotalCount by remember { mutableIntStateOf(0) }
    var deleteCurrentFileName by remember { mutableStateOf("") }
    var deleteIsTrash by remember { mutableStateOf(true) }

    val searchFilters = remember {
        listOf(
            SearchFilterCategory("all", "All"),
            SearchFilterCategory("video", "Videos", "movie"),
            SearchFilterCategory("image", "Images", "image"),
            SearchFilterCategory("audio", "Audio", "music_note"),
            SearchFilterCategory("doc", "Documents", "description"),
            SearchFilterCategory("apk", "APKs", "android"),
            SearchFilterCategory("archive", "Archives", "inventory_2"),
            SearchFilterCategory("large", "> 1 GB", "storage"),
            SearchFilterCategory("old", "Old Downloads", "history")
        )
    }
    var selectedSearchFilter by remember { mutableStateOf("all") }
    var selectedSortOrder by remember { mutableStateOf("largest") }

    var recentSearches by remember(searchQuery) {
        mutableStateOf(FavoritesManager.getRecentSearches(context))
    }

    val appsContainer = rootNode.children?.firstOrNull { it.name == "Apps & System Packages" }
    val largeApps = remember(appsContainer) {
        appsContainer?.children?.sortedByDescending { it.size }?.take(15) ?: emptyList()
    }

    val largestEntries = remember(topFiles, largeApps) {
        val filesList = topFiles
        val appsList = largeApps.map { appNode ->
            TopFileEntry(
                node = appNode,
                path = "Apps & System Packages/${appNode.name}"
            )
        }
        (filesList + appsList).sortedByDescending { it.node.size }
    }
    val displayedTop5 = remember(largestEntries) {
        largestEntries.take(5)
    }

    val searchResults = remember(rootNode, searchQuery, selectedSearchFilter, selectedSortOrder) {
        fun isSystemEntry(entry: TopFileEntry): Boolean {
            val name = entry.node.name.trim().lowercase().removeSurrounding("[", "]")
            return name in setOf("system & os", "free space", "recycle bin", "trashed") ||
                    entry.path.contains("[system", ignoreCase = true) ||
                    entry.path.contains("[free space", ignoreCase = true) ||
                    entry.path.contains("[recycle bin", ignoreCase = true)
        }

        val baseList = if (searchQuery.isNotBlank()) {
            StorageFilterHelper.searchTree(rootNode, searchQuery).filterNot { isSystemEntry(it) }
        } else if (selectedSearchFilter != "all") {
            when (selectedSearchFilter) {
                "large" -> StorageFilterHelper.filterByPreset(rootNode, "> 1 GB", context).filterNot { isSystemEntry(it) }
                "old" -> StorageFilterHelper.filterByPreset(rootNode, "Old Downloads", context).filterNot { isSystemEntry(it) }
                else -> {
                    val results = mutableListOf<TopFileEntry>()
                    fun collect(node: CompactNode, currentPath: String) {
                        val fullPath = if (currentPath.isEmpty()) node.name else "$currentPath/${node.name}"
                        val ext = node.name.substringAfterLast('.', "").lowercase()
                        if (!node.isDirectory && node.size > 0) {
                            val matches = when (selectedSearchFilter) {
                                "video" -> ext in setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v")
                                "image" -> ext in setOf("jpg", "jpeg", "png", "webp", "heic", "gif", "bmp", "raw", "svg")
                                "audio" -> ext in setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma")
                                "doc" -> ext in setOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub")
                                "apk" -> ext in setOf("apk", "xapk", "apks", "aab", "obb") || node.children?.any { it.name.startsWith("App Code") } == true
                                "archive" -> ext in setOf("zip", "rar", "7z", "tar", "gz", "iso", "bin", "xz", "bz2")
                                else -> false
                            }
                            if (matches) {
                                results.add(TopFileEntry(node, fullPath))
                            }
                        }
                        node.children?.forEach { collect(it, fullPath) }
                    }
                    collect(rootNode, "")
                    results.filterNot { isSystemEntry(it) }.sortedByDescending { it.node.size }
                }
            }
        } else {
            emptyList()
        }

        if (searchQuery.isNotBlank() && selectedSearchFilter != "all") {
            val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v", "mpg", "mpeg", "vob")
            val imageExts = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "raw", "svg", "gif", "bmp", "ico", "dng", "cr2", "nef")
            val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid", "midi", "alac", "amr")
            val docExts = setOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub", "mobi", "log", "rtf", "html", "htm", "json", "xml", "md", "yaml", "yml")
            val apkExts = setOf("apk", "xapk", "apks", "aab", "obb")
            val archiveExts = setOf("zip", "rar", "7z", "tar", "gz", "iso", "bin", "img", "dmg", "xz", "bz2", "tgz")

            baseList.filter { entry ->
                val ext = entry.node.name.substringAfterLast('.', "").lowercase()
                when (selectedSearchFilter) {
                    "video" -> ext in videoExts
                    "image" -> ext in imageExts
                    "audio" -> ext in audioExts
                    "doc" -> ext in docExts
                    "apk" -> ext in apkExts || entry.node.children?.any { it.name.startsWith("App Code") } == true
                    "archive" -> ext in archiveExts
                    "large" -> entry.node.size >= 1024L * 1024L * 1024L
                    "old" -> entry.path.contains("/download", ignoreCase = true)
                    else -> true
                }
            }
        } else {
            baseList
        }.let { list ->
            when (selectedSortOrder) {
                "largest" -> list.sortedByDescending { it.node.size }
                "smallest" -> list.sortedBy { it.node.size }
                "oldest" -> list.sortedBy { java.io.File(it.path).lastModified() }
                "newest" -> list.sortedByDescending { java.io.File(it.path).lastModified() }
                else -> list
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = 116.dp)
        ) {
            if (isSearchActive || searchQuery.isNotBlank()) {
                discoverSearchResultsSection(
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    searchFilters = searchFilters,
                    selectedSearchFilter = selectedSearchFilter,
                    selectedSortOrder = selectedSortOrder,
                    recentSearches = recentSearches,
                    selectedEntries = selectedEntries,
                    onSelectFilter = { selectedSearchFilter = it },
                    onSelectSort = { selectedSortOrder = it },
                    onSearchQueryChange = onSearchQueryChange,
                    onClearRecentSearches = {
                        FavoritesManager.clearRecentSearches(context)
                        recentSearches = emptyList()
                    },
                    onToggleSelect = { entry ->
                        selectedEntries = if (selectedEntries.contains(entry)) selectedEntries - entry else selectedEntries + entry
                    },
                    onNodeClick = onNodeClick
                )
            } else {
                item { Spacer(modifier = Modifier.height(16.dp)) }

                discoverLargeItemsSection(
                    displayedTop5 = displayedTop5,
                    selectedEntries = selectedEntries,
                    onToggleSelect = { entry ->
                        selectedEntries = if (selectedEntries.contains(entry)) selectedEntries - entry else selectedEntries + entry
                    },
                    onNodeClick = onNodeClick,
                    onNodesDeleted = onNodesDeleted,
                    onRefresh = onRefresh,
                    onNavigateTo = onNavigateTo
                )

                discoverToolsSection(onNavigateTo = onNavigateTo)
            }
        }

        DiscoverSelectionBar(
            selectedEntries = selectedEntries,
            onClearSelection = { selectedEntries = emptySet() },
            onDeleteClick = { showDeleteDialog = true }
        )

        if (showDeleteDialog && selectedEntries.isNotEmpty()) {
            val allAlreadyTrashed = selectedEntries.all { it.node.name.startsWith(".trashed") || it.path.contains(".trashed") }
            DiscoverDeleteConfirmDialog(
                selectedEntries = selectedEntries,
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    showDeleteDialog = false
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val itemsToDelete = selectedEntries.toList()
                    val (starredItems, unstarredItems) = itemsToDelete.partition { entry ->
                        FavoritesManager.isStarred(context, entry.path)
                    }

                    if (unstarredItems.isEmpty()) {
                        AppNotifier.notify("Cannot delete starred files. Unstar them manually first.")
                        return@DiscoverDeleteConfirmDialog
                    }

                    scope.launch {
                        isDeleting = true
                        deleteTotalCount = unstarredItems.size
                        deleteIsTrash = !allAlreadyTrashed
                        var processedCount = 0
                        val packagesToUninstall = mutableListOf<String>()

                        withContext(Dispatchers.IO) {
                            unstarredItems.forEachIndexed { index, entry ->
                                deleteCurrentCount = index + 1
                                deleteCurrentFileName = entry.node.name
                                AppNotifier.updateProgress(
                                    context = context,
                                    title = if (allAlreadyTrashed) "Deleting permanently..." else "Moving to Recycle Bin...",
                                    detail = "${index + 1} / ${unstarredItems.size}: ${entry.node.name}",
                                    progress = index + 1,
                                    max = unstarredItems.size,
                                    indeterminate = false,
                                    type = "delete"
                                )
                                val pkg = FileUtils.extractPackageName(entry.node, entry.path, context)
                                if (pkg != null) {
                                    packagesToUninstall.add(pkg)
                                } else {
                                    try {
                                        val f = FileUtils.resolveActualFile(entry.path) ?: FileUtils.resolveActualFile(entry.node.name)
                                        if (f != null && f.exists()) {
                                            if (FileUtils.deleteOrTrashFile(f, context)) {
                                                processedCount++
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }

                        if (packagesToUninstall.isNotEmpty()) {
                            FileUtils.uninstallApps(context, packagesToUninstall)
                        }

                        isDeleting = false
                        selectedEntries = emptySet()
                        val baseMsg = if (allAlreadyTrashed) "Deleted $processedCount items permanently" else "Moved $processedCount items to Recycle Bin"
                        val msg = if (starredItems.isNotEmpty()) "$baseMsg (Skipped ${starredItems.size} starred items)" else baseMsg
                        AppNotifier.finishActivity(context, msg)
                        val deletedNodes = unstarredItems.map { it.node }.toSet()
                        onNodesDeleted(deletedNodes)
                    }
                }
            )
        }

        DeletionProgressDialog(
            visible = isDeleting,
            currentCount = deleteCurrentCount,
            totalCount = deleteTotalCount,
            currentFileName = deleteCurrentFileName,
            isTrash = deleteIsTrash
        )
    }
}
