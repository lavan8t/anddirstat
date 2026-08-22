package com.kd.anddirstat.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kd.anddirstat.ui.components.AppTooltip
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var selectedNodes by remember(rootNode, searchQuery) { mutableStateOf(setOf<CompactNode>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = 110.dp)
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
                    itemsIndexed(
                        items = searchResults,
                        key = { _, entry -> entry.path }
                    ) { index, entry ->
                        val shape = when {
                            searchResults.size == 1 -> RoundedCornerShape(24.dp)
                            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                            index == searchResults.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                            else -> RoundedCornerShape(4.dp)
                        }
                        val isApp = entry.node.children?.any { it.name.startsWith("App Code") } == true
                        val appPkg = if (isApp) FileUtils.extractPackageName(entry.node) else null
                        val isSelectable = remember(entry.node) {
                            val n = entry.node.name.trim().lowercase()
                            n != "[system & os]" && n != "system & os" &&
                            n != "[recycle bin]" && n != "recycle bin" && n != "trashed" &&
                            n != "[free space]" && n != "free space"
                        }
                        val isSelected = isSelectable && selectedNodes.contains(entry.node)

                        Surface(
                            shape = shape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (selectedNodes.isNotEmpty() && isSelectable) {
                                            selectedNodes = if (isSelected) selectedNodes - entry.node else selectedNodes + entry.node
                                        } else {
                                            onNodeClick(entry.node, entry.path)
                                        }
                                    },
                                    onLongClick = {
                                        if (isSelectable) {
                                            selectedNodes = if (isSelected) selectedNodes - entry.node else selectedNodes + entry.node
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
                                                    selectedNodes = if (isSelected) selectedNodes - entry.node else selectedNodes + entry.node
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
                                        Icon(
                                            imageVector = FileUtils.getNodeIcon(entry.node, false),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.node.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
            } else {
            // 1. Filter Presets Row using official FilterChips
            item {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                val pkgName = remember(app) { FileUtils.extractPackageName(app) }
                val appChildren = app.children
                val cacheSize = appChildren?.firstOrNull { it.name == "Cache" }?.size ?: 0L

                val shape = when {
                    largeApps.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                    index == largeApps.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(4.dp)
                }

                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
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
                                    text = "${FileUtils.formatFileSize(app.size)} • ${FileUtils.formatFileSize(cacheSize)} cache",
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
                            if (pkgName != null) {
                                val launchIntent = remember(pkgName) { context.packageManager.getLaunchIntentForPackage(pkgName) }
                                if (launchIntent != null) {
                                    AppTooltip(text = "Open app") {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    try {
                                                        context.startActivity(launchIntent)
                                                    } catch (_: Exception) {}
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                MaterialSymbol(
                                                    name = "open_in_new",
                                                    active = true,
                                                    size = 18.dp,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            AppTooltip(text = "App details") {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (pkgName != null) {
                                                try {
                                                    context.startActivity(
                                                        Intent(
                                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                            Uri.parse("package:$pkgName")
                                                        )
                                                    )
                                                } catch (_: Exception) {}
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        MaterialSymbol(
                                            name = "info",
                                            active = true,
                                            size = 18.dp,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            AppTooltip(text = "Uninstall") {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (pkgName != null) {
                                                FileUtils.uninstallApp(context, pkgName)
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

                if (index < largeApps.lastIndex) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }

    // Floating Selection Bar for Search Results
        AnimatedVisibility(
            visible = selectedNodes.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppTooltip(text = "Clear selection") {
                            IconButton(onClick = { selectedNodes = emptySet() }) {
                                MaterialSymbol(
                                    name = "close",
                                    active = true,
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${selectedNodes.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    AppTooltip(text = "Delete selected") {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { showDeleteDialog = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialSymbol(
                                    name = "delete",
                                    active = true,
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog && selectedNodes.isNotEmpty()) {
            val count = selectedNodes.size
            val totalBytes = selectedNodes.sumOf { it.size }
            val hasApps = selectedNodes.any { FileUtils.extractPackageName(it) != null }
            val allApps = selectedNodes.all { FileUtils.extractPackageName(it) != null }

            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        text = when {
                            allApps && count == 1 -> "Uninstall 1 app?"
                            allApps -> "Uninstall $count apps?"
                            hasApps -> "Delete / Uninstall $count items?"
                            count == 1 -> "Delete 1 item?"
                            else -> "Delete $count items?"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This action is permanent and cannot be undone.\n\n${FileUtils.formatFileSize(totalBytes)} will be freed permanently",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            val packagesToUninstall = mutableListOf<String>()
                            selectedNodes.forEach { node ->
                                val pkg = FileUtils.extractPackageName(node)
                                if (pkg != null) {
                                    packagesToUninstall.add(pkg)
                                } else {
                                    try {
                                        val f = FileUtils.resolveActualFile(node.name)
                                        if (f != null && f.exists()) f.deleteRecursively()
                                    } catch (_: Exception) {}
                                }
                            }
                            if (packagesToUninstall.isNotEmpty()) {
                                FileUtils.uninstallApps(context, packagesToUninstall)
                            }
                            selectedNodes = emptySet()
                        }
                    ) {
                        Text(
                            text = if (allApps) "Uninstall" else if (hasApps) "Delete / Uninstall" else "Delete",
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
    }
}
