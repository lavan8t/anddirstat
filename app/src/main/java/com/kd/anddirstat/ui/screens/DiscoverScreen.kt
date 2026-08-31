package com.kd.anddirstat.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.AppTooltip
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class SearchFilterCategory(
    val id: String,
    val label: String,
    val icon: String? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var selectedSortOrder by remember { mutableStateOf("largest") } // largest, smallest, oldest, newest

    var recentSearches by remember(searchQuery) {
        mutableStateOf(com.kd.anddirstat.util.FavoritesManager.getRecentSearches(context))
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


    // System/OS node names to exclude from search (also strip brackets for display)
    val systemNodeNames = remember {
        setOf("system & os", "free space", "recycle bin", "trashed", "android", "data/data", ".android_secure")
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
                "apk" -> StorageFilterHelper.filterByPreset(rootNode, "APKs", context).filterNot { isSystemEntry(it) }
                else -> {
                    val results = mutableListOf<TopFileEntry>()
                    val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v", "mpg", "mpeg", "vob")
                    val imageExts = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "raw", "svg", "gif", "bmp", "ico", "dng", "cr2", "nef")
                    val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid", "midi", "alac", "amr")
                    val docExts = setOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub", "mobi", "log", "rtf", "html", "htm", "json", "xml", "md", "yaml", "yml")
                    val archiveExts = setOf("zip", "rar", "7z", "tar", "gz", "iso", "bin", "img", "dmg", "xz", "bz2", "tgz")

                    fun collect(node: CompactNode, path: String) {
                        val fullPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                        if (!node.isDirectory) {
                            val ext = node.name.substringAfterLast('.', "").lowercase()
                            val matches = when (selectedSearchFilter) {
                                "video" -> ext in videoExts
                                "image" -> ext in imageExts
                                "audio" -> ext in audioExts
                                "doc" -> ext in docExts
                                "archive" -> ext in archiveExts
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
                // Filter chips row in Search mode
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(searchFilters) { filter ->
                            val isSelected = selectedSearchFilter == filter.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedSearchFilter = filter.id
                                },
                                leadingIcon = if (filter.icon != null) {
                                    {
                                        MaterialSymbol(
                                            name = filter.icon,
                                            active = isSelected,
                                            size = 16.dp,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else null,
                                label = {
                                    Text(
                                        text = filter.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                border = null,
                                modifier = Modifier.height(36.dp)
                            )
                        }
                    }
                }

                if (searchQuery.isNotBlank() || selectedSearchFilter != "all") {
                    // Sort chips row
                    item {
                        val sortOptions = listOf(
                            "largest" to "Largest",
                            "smallest" to "Smallest",
                            "newest" to "Newest",
                            "oldest" to "Oldest"
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sortOptions) { (id, label) ->
                                val isSelected = selectedSortOrder == id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedSortOrder = id
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    border = null,
                                    modifier = Modifier.height(34.dp)
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "${searchResults.size} results found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    if (searchResults.isEmpty()) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp)
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No files found matching \"$searchQuery\"" else "No files found for selected filter",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = searchResults,
                            key = { index, entry -> "${entry.path}_$index" }
                        ) { index, entry ->
                            val shape = when {
                                searchResults.size == 1 -> RoundedCornerShape(24.dp)
                                index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                index == searchResults.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                else -> RoundedCornerShape(4.dp)
                            }
                            val isApp = entry.node.children?.any { it.name.startsWith("App Code") } == true
                            val appPkg = if (isApp) FileUtils.extractPackageName(entry.node, entry.path, context) else null
                            val isSelectable = remember(entry.node) {
                                val n = entry.node.name.trim().lowercase()
                                n != "[system & os]" && n != "system & os" &&
                                n != "[recycle bin]" && n != "recycle bin" && n != "trashed" &&
                                n != "[free space]" && n != "free space"
                            }
                            val isSelected = isSelectable && selectedEntries.contains(entry)

                            Surface(
                                shape = shape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clip(shape)
                                    .combinedClickable(
                                            onClick = {
                                                if (selectedEntries.isNotEmpty() && isSelectable) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedEntries = if (isSelected) selectedEntries - entry else selectedEntries + entry
                                                } else {
                                                    onNodeClick(entry.node, entry.path)
                                                }
                                            },
                                            onLongClick = {
                                                if (isSelectable) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedEntries = if (isSelected) selectedEntries - entry else selectedEntries + entry
                                                }
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .then(
                                                    if (isSelectable) {
                                                        Modifier.clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            selectedEntries = if (isSelected) selectedEntries - entry else selectedEntries + entry
                                                        }
                                                    } else Modifier
                                                )
                                        ) {
                                            if (appPkg != null) {
                                                AppIconView(
                                                    packageName = appPkg,
                                                    contentDescription = entry.node.name,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                MediaThumbnailView(
                                                    node = entry.node,
                                                    path = entry.path,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(MaterialTheme.colorScheme.primary),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    MaterialSymbol(
                                                        name = "check",
                                                        active = true,
                                                        size = 24.dp,
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        val isStarred = remember(entry.path) { com.kd.anddirstat.util.FavoritesManager.isStarred(context, entry.path) }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = entry.node.name.removeSurrounding("[", "]"),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (isStarred) {
                                                    MaterialSymbol(
                                                        name = "star",
                                                        active = true,
                                                        size = 18.dp,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = FileUtils.formatFileSize(entry.node.size),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                val folderName = remember(entry.path) {
                                                    val parts = entry.path.trimEnd('/').split('/')
                                                    if (parts.size > 1) parts[parts.size - 2] else parts.firstOrNull() ?: ""
                                                }
                                                Text(
                                                    text = folderName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                            if (index < searchResults.lastIndex) {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                } else if (recentSearches.isNotEmpty()) {
                    // Search bar is opened/focused: show Recent Searches chips
                    item(key = "recent_searches_section") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            com.kd.anddirstat.util.FavoritesManager.clearRecentSearches(context)
                                            recentSearches = emptyList()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(recentSearches) { query ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSearchQueryChange?.invoke(query)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            MaterialSymbol(
                                                name = "history",
                                                active = false,
                                                size = 16.dp,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = query,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Large Items List (Top 5 ranked by size with M3 swipe-revealed actions)
                if (displayedTop5.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Large Items",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    itemsIndexed(
                        items = displayedTop5,
                        key = { index, entry -> "top5_${entry.path}_${entry.node.name}_$index" }
                    ) { index, entry ->
                        val pkgName = remember(entry.node, entry.path) {
                            FileUtils.extractPackageName(entry.node, entry.path, context)
                                ?: FileUtils.AppPackageRegistry.getPackageName(entry.node.name)
                        }
                        val isApp = pkgName != null ||
                                entry.path.startsWith("Apps & System Packages") ||
                                entry.node.children?.any { it.name.startsWith("App Code") } == true
                        val isSelected = selectedEntries.contains(entry)

                        val shape = when {
                            displayedTop5.size == 1 -> RoundedCornerShape(20.dp)
                            index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                            index == displayedTop5.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                            else -> RoundedCornerShape(4.dp)
                        }

                        val density = LocalDensity.current
                        val revealWidth = 58.dp
                        val revealWidthPx = with(density) { revealWidth.toPx() }
                        val offsetX = remember(entry) { Animatable(0f) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(shape)
                                .animateItem()
                        ) {
                            // Revealed Action Button beside the card (Material 3 swipe action)
                            Row(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(end = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(revealWidth)
                                        .fillMaxHeight()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch { offsetX.animateTo(0f) }
                                            if (isApp) {
                                                val targetPkg = pkgName ?: entry.node.name
                                                FileUtils.uninstallApp(context, targetPkg)
                                            } else {
                                                if (com.kd.anddirstat.util.FavoritesManager.isStarred(context, entry.path)) {
                                                    com.kd.anddirstat.util.AppNotifier.notify("Cannot delete starred file. Unstar manually first.")
                                                } else {
                                                    scope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            try {
                                                                val f = FileUtils.resolveActualFile(entry.path) ?: FileUtils.resolveActualFile(entry.node.name)
                                                                if (f != null && f.exists()) {
                                                                    FileUtils.deleteOrTrashFile(f, context)
                                                                }
                                                            } catch (_: Exception) {}
                                                        }
                                                        onNodesDeleted(setOf(entry.node))
                                                        onRefresh()
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    MaterialSymbol(
                                        name = if (isApp) "delete_forever" else "delete",
                                        active = true,
                                        size = 22.dp,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                        // Foreground Surface Card
                        Surface(
                            shape = shape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                                .pointerInput(entry) {
                                    var lastHapticZone = 0
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            lastHapticZone = 0
                                            scope.launch {
                                                val curr = offsetX.value
                                                if (curr <= -revealWidthPx * 0.4f) {
                                                    // Snap to revealed position (never auto-delete on full swipe)
                                                    offsetX.animateTo(-revealWidthPx, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                                } else {
                                                    // Snap back
                                                    offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                                }
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            val nextX = (offsetX.value + dragAmount).coerceIn(-revealWidthPx * 1.35f, 0f)
                                            val zone = if (-nextX >= revealWidthPx) 1 else 0
                                            if (zone != lastHapticZone) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                lastHapticZone = zone
                                            }
                                            scope.launch { offsetX.snapTo(nextX) }
                                        }
                                    )
                                }
                                    .clickable {
                                        if (offsetX.value < -10f) {
                                            scope.launch { offsetX.animateTo(0f) }
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (selectedEntries.isNotEmpty()) {
                                                selectedEntries = if (isSelected) selectedEntries - entry else selectedEntries + entry
                                            } else if (isApp && pkgName != null) {
                                                try {
                                                    context.startActivity(
                                                        Intent(
                                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                            "package:$pkgName".toUri()
                                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                    )
                                                } catch (_: Exception) {}
                                            } else {
                                                onNodeClick(entry.node, entry.path)
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(44.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isApp && pkgName != null) {
                                            AppIconView(
                                                packageName = pkgName,
                                                contentDescription = entry.node.name,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        } else {
                                            val iconColor = when {
                                                entry.node.name.endsWith(".apk", true) -> MaterialTheme.colorScheme.error
                                                entry.node.name.endsWith(".zip", true) || entry.node.name.endsWith(".iso", true) -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.tertiary
                                            }
                                            MediaThumbnailView(
                                                node = entry.node,
                                                path = entry.path,
                                                fallbackTint = iconColor,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = entry.node.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = FileUtils.formatFileSize(entry.node.size, context),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isApp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (index < displayedTop5.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateTo?.invoke(com.kd.anddirstat.model.AppDestinations.LARGEST_FILES)
                                }
                            ) {
                                Text(
                                    text = "More",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

            // 4. Utility Tools Section at End
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item(key = "utility_tools_grid") {
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    val isWide = maxWidth >= 600.dp
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Tool 1: Screenshots Cleaner
                            Card(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateTo?.invoke(com.kd.anddirstat.model.AppDestinations.CLEANER_SCREENSHOTS)
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(108.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MaterialSymbol(
                                        name = "screenshot_monitor",
                                        active = true,
                                        size = 28.dp,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Screenshots Cleaner",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Tool 2: Empty Folders Cleaner
                            Card(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateTo?.invoke(com.kd.anddirstat.model.AppDestinations.CLEANER_EMPTY_FOLDERS)
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(108.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MaterialSymbol(
                                        name = "folder_open",
                                        active = true,
                                        size = 28.dp,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "Empty Folders",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Tool 3: Duplicates Cleaner
                            Card(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateTo?.invoke(com.kd.anddirstat.model.AppDestinations.CLEANER_DUPLICATES)
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(108.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MaterialSymbol(
                                        name = "content_copy",
                                        active = true,
                                        size = 28.dp,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = "Duplicates",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Tool 1: Screenshots Cleaner
                                Card(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigateTo?.invoke(com.kd.anddirstat.model.AppDestinations.CLEANER_SCREENSHOTS)
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(108.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        MaterialSymbol(
                                            name = "screenshot_monitor",
                                            active = true,
                                            size = 28.dp,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Screenshots Cleaner",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Tool 2: Empty Folders Cleaner
                                Card(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigateTo?.invoke(com.kd.anddirstat.model.AppDestinations.CLEANER_EMPTY_FOLDERS)
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(108.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        MaterialSymbol(
                                            name = "folder_open",
                                            active = true,
                                            size = 28.dp,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = "Empty Folders",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Tool 3: Duplicates Cleaner
                            Card(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateTo?.invoke(com.kd.anddirstat.model.AppDestinations.CLEANER_DUPLICATES)
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    MaterialSymbol(
                                        name = "content_copy",
                                        active = true,
                                        size = 28.dp,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Duplicates Cleaner",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Find and remove duplicate files",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    MaterialSymbol(
                                        name = "chevron_right",
                                        active = true,
                                        size = 20.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

        // Floating Selection Bar for Search Results (Rich, expressive, sitting right above bottom navbar)
        AnimatedVisibility(
            visible = selectedEntries.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn() + scaleIn(initialScale = 0.92f),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 3.dp,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppTooltip(text = "Clear selection") {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(42.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedEntries = emptySet()
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    MaterialSymbol(
                                        name = "close",
                                        active = true,
                                        size = 22.dp,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Column {
                            Text(
                                text = "${selectedEntries.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = FileUtils.formatFileSize(selectedEntries.sumOf { it.node.size }, context),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDeleteDialog = true
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MaterialSymbol(
                                name = "delete",
                                active = true,
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onError
                            )
                            Text(
                                text = "Delete",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteDialog && selectedEntries.isNotEmpty()) {
            val count = selectedEntries.size
            val totalBytes = selectedEntries.sumOf { it.node.size }
            val hasApps = selectedEntries.any { FileUtils.extractPackageName(it.node, it.path, context) != null }
            val allApps = selectedEntries.all { FileUtils.extractPackageName(it.node, it.path, context) != null }
            val allAlreadyTrashed = selectedEntries.all { it.node.name.startsWith(".trashed") || it.path.contains(".trashed") }

            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = {
                    MaterialSymbol(
                        name = "delete",
                        active = true,
                        size = 28.dp,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        text = when {
                            allApps && count == 1 -> "Uninstall 1 app?"
                            allApps -> "Uninstall $count apps?"
                            hasApps -> "Delete / Uninstall $count items?"
                            allAlreadyTrashed && count == 1 -> "Delete 1 item permanently?"
                            allAlreadyTrashed -> "Delete $count items permanently?"
                            count == 1 -> "Move 1 item to Recycle Bin?"
                            else -> "Move $count items to Recycle Bin?"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = when {
                            allApps -> "$count applications will be uninstalled from device."
                            allAlreadyTrashed -> "This action is permanent and cannot be undone.\n\n${FileUtils.formatFileSize(totalBytes)} will be freed permanently"
                            else -> "Selected items will be moved to the Recycle Bin.\n\n${FileUtils.formatFileSize(totalBytes)} to be moved"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val itemsToDelete = selectedEntries.toList()
                            val (starredItems, unstarredItems) = itemsToDelete.partition { entry ->
                                com.kd.anddirstat.util.FavoritesManager.isStarred(context, entry.path)
                            }

                            if (unstarredItems.isEmpty()) {
                                com.kd.anddirstat.util.AppNotifier.notify("Cannot delete starred files. Unstar them manually first.")
                                return@TextButton
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
                                com.kd.anddirstat.util.AppNotifier.finishActivity(context, msg)
                                val deletedNodes = unstarredItems.map { it.node }.toSet()
                                onNodesDeleted(deletedNodes)
                            }
                        }
                    ) {
                        Text(
                            text = if (allApps) "Uninstall" else if (hasApps) "Delete / Uninstall" else if (allAlreadyTrashed) "Delete" else "Move to Bin",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
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
