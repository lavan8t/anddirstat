package com.kd.anddirstat.ui.screens.discover

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils

data class SearchFilterCategory(
    val id: String,
    val label: String,
    val icon: String? = null
)

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.discoverSearchResultsSection(
    searchQuery: String,
    searchResults: List<TopFileEntry>,
    searchFilters: List<SearchFilterCategory>,
    selectedSearchFilter: String,
    selectedSortOrder: String,
    recentSearches: List<String>,
    selectedEntries: Set<TopFileEntry>,
    onSelectFilter: (String) -> Unit,
    onSelectSort: (String) -> Unit,
    onSearchQueryChange: ((String) -> Unit)?,
    onClearRecentSearches: () -> Unit,
    onToggleSelect: (TopFileEntry) -> Unit,
    onNodeClick: (CompactNode, String) -> Unit
) {
    // Filter chips row
    item(key = "search_filter_chips") {
        val haptic = LocalHapticFeedback.current
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
                        onSelectFilter(filter.id)
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
        item(key = "search_sort_chips") {
            val haptic = LocalHapticFeedback.current
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
                            onSelectSort(id)
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

        item(key = "search_results_count") {
            Text(
                text = "${searchResults.size} results found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        if (searchResults.isEmpty()) {
            item(key = "search_results_empty") {
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
                DiscoverSearchResultRow(
                    index = index,
                    totalCount = searchResults.size,
                    entry = entry,
                    isSelected = selectedEntries.contains(entry),
                    isSelectionMode = selectedEntries.isNotEmpty(),
                    onToggleSelect = { onToggleSelect(entry) },
                    onNodeClick = onNodeClick
                )
                if (index < searchResults.lastIndex) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    } else if (recentSearches.isNotEmpty()) {
        // Show Recent Searches
        item(key = "recent_searches_section") {
            val haptic = LocalHapticFeedback.current
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
                                onClearRecentSearches()
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverSearchResultRow(
    index: Int,
    totalCount: Int,
    entry: TopFileEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onNodeClick: (CompactNode, String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val shape = when {
        totalCount == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        index == totalCount - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
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

    Surface(
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && isSelectable) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleSelect()
                    } else {
                        onNodeClick(entry.node, entry.path)
                    }
                },
                onLongClick = {
                    if (isSelectable) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleSelect()
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
                                onToggleSelect()
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
            val isStarred = remember(entry.path) { FavoritesManager.isStarred(context, entry.path) }
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
}
