package com.kd.anddirstat.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverView(
    rootNode: CompactNode,
    topFiles: List<TopFileEntry>,
    searchQuery: String = "",
    onNodeClick: (CompactNode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPreset by remember { mutableStateOf<String?>("> 1 GB") }

    val filterPresets = remember {
        listOf("> 1 GB", "Duplicates", "Old Downloads", "APKs")
    }

    val displayedFiles = remember(rootNode, selectedPreset, topFiles) {
        if (selectedPreset != null) {
            val res = StorageFilterHelper.filterByPreset(rootNode, selectedPreset!!)
            if (res.isNotEmpty()) res else topFiles.take(10)
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
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
                items(
                    items = searchResults,
                    key = { it.path }
                ) { entry ->
                    ListItem(
                        leadingContent = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = FileUtils.getNodeIcon(entry.node, false),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        },
                        headlineContent = {
                            Text(
                                text = entry.node.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
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
                                    modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp)
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            onNodeClick(entry.node, entry.path)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
        } else {
            // 1. Filter Presets Row
            item {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(IntrinsicSize.Min)
                            ) {
                                filterPresets.forEachIndexed { index, preset ->
                                    val isSelected = selectedPreset == preset
                                    Surface(
                                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                        shape = when (index) {
                                            0 -> RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                                            filterPresets.lastIndex -> RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
                                            else -> RoundedCornerShape(0.dp)
                                        },
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .clickable {
                                                selectedPreset = if (isSelected) null else preset
                                            }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        ) {
                                            Text(
                                                text = preset,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }

                                    if (index < filterPresets.lastIndex) {
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .fillMaxHeight(0.55f)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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

            // 2. Largest Files Horizontal Carousel with Efficient Lazy Thumbnails
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Largest Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = displayedFiles,
                            key = { it.path }
                        ) { entry ->
                            val isApk = entry.node.name.endsWith(".apk", ignoreCase = true) || entry.node.name.startsWith("App Code")
                            val isBinary = !entry.node.isDirectory && (entry.node.name.endsWith(".iso", ignoreCase = true) || entry.node.name.endsWith(".bin", ignoreCase = true) || entry.node.name.endsWith(".zip", ignoreCase = true))
                            val iconColor = if (isApk) {
                                MaterialTheme.colorScheme.error
                            } else if (isBinary) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }

                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),

                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(220.dp)
                                    .clickable { onNodeClick(entry.node, entry.path) }
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(2f)
                                    ) {
                                        MediaThumbnailView(
                                            node = entry.node,
                                            path = entry.path,
                                            fallbackTint = iconColor,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1.2f)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = entry.node.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = FileUtils.formatFileSize(entry.node.size),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
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

            // 3. Large Apps Vertical List
            item {
                Text(
                    text = "Large Apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            itemsIndexed(
                items = largeApps,
                key = { index, app -> "${app.name}_$index" }
            ) { _, app ->
                val pkgName = remember(app) { FileUtils.extractPackageName(app) }
                val appChildren = app.children
                val cacheSize = appChildren?.firstOrNull { it.name == "Cache" }?.size ?: 0L

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconView(
                                packageName = pkgName,
                                contentDescription = app.name,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${FileUtils.formatFileSize(app.size)} • Cache: ${FileUtils.formatFileSize(cacheSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (pkgName != null) {
                                            try {
                                                val intent = Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.parse("package:$pkgName")
                                                )
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    MaterialSymbol(
                                        name = "info",
                                        active = true,
                                        size = 20.dp,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (pkgName != null) {
                                            try {
                                                val intent = Intent(
                                                    Intent.ACTION_DELETE,
                                                    Uri.parse("package:$pkgName")
                                                )
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    MaterialSymbol(
                                        name = "delete",
                                        active = true,
                                        size = 18.dp,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
