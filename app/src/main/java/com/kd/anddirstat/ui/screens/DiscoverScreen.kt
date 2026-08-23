package com.kd.anddirstat.ui.screens

import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.kd.anddirstat.DuplicatesCleanerActivity
import com.kd.anddirstat.EmptyFoldersCleanerActivity
import com.kd.anddirstat.RecycleBinCleanerActivity
import com.kd.anddirstat.ScreenshotsCleanerActivity
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.AppTooltip
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var selectedPreset by remember { mutableStateOf<String?>("> 1 GB") }
    var selectedEntries by remember(rootNode, searchQuery) { mutableStateOf(setOf<TopFileEntry>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var isDeleting by remember { mutableStateOf(false) }
    var deleteCurrentCount by remember { mutableIntStateOf(0) }
    var deleteTotalCount by remember { mutableIntStateOf(0) }
    var deleteCurrentFileName by remember { mutableStateOf("") }
    var deleteIsTrash by remember { mutableStateOf(true) }

    val filterPresets = remember {
        listOf("Starred", "> 1 GB", "Old Downloads", "APKs")
    }

    var recentSearches by remember(searchQuery) {
        mutableStateOf(com.kd.anddirstat.util.FavoritesManager.getRecentSearches(context))
    }

    val presetListResults = remember(rootNode, selectedPreset) {
        if (selectedPreset == "Old Downloads" || selectedPreset == "APKs") {
            StorageFilterHelper.filterByPreset(rootNode, selectedPreset!!, context)
        } else emptyList()
    }

    val displayedFiles = remember(rootNode, selectedPreset, topFiles) {
        if (selectedPreset != null) {
            val res = StorageFilterHelper.filterByPreset(rootNode, selectedPreset!!, context)
            res.ifEmpty { topFiles.take(10) }
        } else {
            topFiles.take(10)
        }
    }

    val appsContainer = rootNode.children?.firstOrNull { it.name == "Apps & System Packages" }
    val largeApps = remember(appsContainer) {
        appsContainer?.children?.sortedByDescending { it.size }?.take(15) ?: emptyList()
    }

    val searchResults = remember(rootNode, searchQuery) {
        if (searchQuery.isNotBlank()) {
            StorageFilterHelper.searchTree(rootNode, searchQuery)
        } else {
            emptyList()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            if (isSearchActive || searchQuery.isNotBlank()) {
                if (searchQuery.isNotBlank()) {
                    item {
                        Text(
                            text = "${searchResults.size} results found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
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
                                    text = "No files found matching \"$searchQuery\"",
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
                                                    text = entry.node.name,
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
                                                Text(
                                                    text = entry.path,
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

            // 1. Filter Presets Row using official FilterChips
            item {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(filterPresets) { index, preset ->
                        val isSelected = selectedPreset == preset
                        val chipShape = when {
                            isSelected -> RoundedCornerShape(20.dp)
                            filterPresets.size == 1 -> RoundedCornerShape(20.dp)
                            index == 0 -> RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                            index == filterPresets.lastIndex -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp)
                            else -> RoundedCornerShape(4.dp)
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedPreset = if (isSelected) null else preset
                            },
                            label = {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = chipShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = null,
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            }

            if (selectedPreset == "Old Downloads" || selectedPreset == "APKs") {
                item {
                    Text(
                        text = "${presetListResults.size} $selectedPreset found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }

                if (presetListResults.isEmpty()) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp)
                        ) {
                            Text(
                                text = "No $selectedPreset found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = presetListResults,
                        key = { index, entry -> "${entry.path}_preset_$index" }
                    ) { index, entry ->
                        val shape = when {
                            presetListResults.size == 1 -> RoundedCornerShape(24.dp)
                            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                            index == presetListResults.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
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
                                            modifier = Modifier.fillMaxSize()
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

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val isStarred = remember(entry.path) { com.kd.anddirstat.util.FavoritesManager.isStarred(context, entry.path) }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = entry.node.name,
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
                                        Text(
                                            text = entry.path.replace(Environment.getExternalStorageDirectory().absolutePath, "Storage"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
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

            // 2. Largest Files Material 3 Expressive Carousel (No gap between files, text inside cover)
            if (displayedFiles.isNotEmpty()) {
                item {
                    Text(
                        text = "Largest Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    val carouselState = rememberCarouselState { displayedFiles.size }
                    val textDropShadow = remember {
                        Shadow(
                            color = Color.Black.copy(alpha = 0.95f),
                            offset = Offset(0f, 2f),
                            blurRadius = 6f
                        )
                    }

                    HorizontalMultiBrowseCarousel(
                        state = carouselState,
                        preferredItemWidth = 186.dp,
                        itemSpacing = 8.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) { index ->
                            val entry = displayedFiles[index]
                            val isApk = entry.node.name.endsWith(".apk", ignoreCase = true) || entry.node.name.startsWith("App Code")
                            val isBinary = !entry.node.isDirectory && (entry.node.name.endsWith(".iso", ignoreCase = true) || entry.node.name.endsWith(".bin", ignoreCase = true) || entry.node.name.endsWith(".zip", ignoreCase = true))
                            val iconColor = if (isApk) {
                                MaterialTheme.colorScheme.error
                            } else if (isBinary) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .maskClip(MaterialTheme.shapes.extraLarge)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { onNodeClick(entry.node, entry.path) }
                            ) {
                                if (isApk) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(16.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            MediaThumbnailView(
                                                node = entry.node,
                                                path = entry.path,
                                                fallbackTint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = FileUtils.middleEllipsis(entry.node.name, 22),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = FileUtils.formatFileSize(entry.node.size, context),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    MediaThumbnailView(
                                        node = entry.node,
                                        path = entry.path,
                                        fallbackTint = iconColor,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Gradient Scrim Overlay for crisp text legibility
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.55f),
                                                        Color.Black.copy(alpha = 0.88f)
                                                    )
                                                )
                                            )
                                    )

                                    // Name and Size Inside Cover
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomStart)
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = FileUtils.middleEllipsis(entry.node.name, 22),
                                            style = MaterialTheme.typography.titleSmall.copy(shadow = textDropShadow),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = FileUtils.formatFileSize(entry.node.size, context),
                                            style = MaterialTheme.typography.bodySmall.copy(shadow = textDropShadow),
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 3. Large Apps Vertical List with Settings-like Grouped Cards
            item {
                Text(
                    text = "Large Apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(
                items = largeApps,
                key = { index, app -> "${app.name}_$index" }
            ) { index, app ->
                val pkgName = remember(app) { FileUtils.extractPackageName(app, null, context) }
                val appChildren = app.children
                val cacheSize = appChildren?.firstOrNull { it.name == "Cache" }?.size ?: 0L
                val appEntry = remember(app, pkgName) { TopFileEntry(app, "package:${pkgName ?: app.name}") }
                val isSelected = selectedEntries.contains(appEntry)

                val shape = when {
                    largeApps.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                    index == largeApps.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(4.dp)
                }

                Surface(
                    shape = shape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(shape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (selectedEntries.isNotEmpty()) {
                                selectedEntries = if (isSelected) selectedEntries - appEntry else selectedEntries + appEntry
                            } else if (pkgName != null) {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            "package:$pkgName".toUri()
                                        )
                                    )
                                } catch (_: Exception) {}
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
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedEntries = if (isSelected) selectedEntries - appEntry else selectedEntries + appEntry
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AppIconView(
                                packageName = pkgName,
                                contentDescription = app.name,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
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

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = pkgName ?: "Application",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FileUtils.formatFileSize(app.size, context),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (index < largeApps.lastIndex) {
                    Spacer(modifier = Modifier.height(2.dp))
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tool 1: Screenshots Cleaner
                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val intent = Intent(context, ScreenshotsCleanerActivity::class.java)
                            context.startActivity(intent)
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
                            val intent = Intent(context, EmptyFoldersCleanerActivity::class.java)
                            context.startActivity(intent)
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tool 3: Recycle Bin
                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val intent = Intent(context, RecycleBinCleanerActivity::class.java)
                            context.startActivity(intent)
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
                                name = "delete_sweep",
                                active = true,
                                size = 28.dp,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Recycle Bin",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Tool 4: Duplicates Cleaner
                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val intent = Intent(context, DuplicatesCleanerActivity::class.java)
                            context.startActivity(intent)
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
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

        // Floating Selection Bar for Search Results (Rich, expressive, sitting right above bottom navbar)
        AnimatedVisibility(
            visible = selectedEntries.isNotEmpty(),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh)
            ) + fadeIn(
                animationSpec = tween(120, easing = FastOutSlowInEasing)
            ) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(stiffness = Spring.StiffnessHigh)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessHigh)
            ) + fadeOut(
                animationSpec = tween(100, easing = FastOutSlowInEasing)
            ) + scaleOut(
                targetScale = 0.92f,
                animationSpec = spring(stiffness = Spring.StiffnessHigh)
            ),
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
                                        com.kd.anddirstat.util.AppNotifier.updateProgress(
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
