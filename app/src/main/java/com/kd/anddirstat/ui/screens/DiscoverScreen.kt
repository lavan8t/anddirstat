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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverView(
    rootNode: CompactNode,
    topFiles: List<TopFileEntry>,
    onNodeClick: (CompactNode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf<String?>("> 1 GB") }

    val primaryCategory = remember(rootNode) {
        StorageFilterHelper.findPrimaryCategory(rootNode)
    }

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
    val heavyApps = remember(appsContainer) {
        appsContainer?.children?.sortedByDescending { it.size }?.take(15) ?: emptyList()
    }

    val searchResults = remember(rootNode, searchQuery) {
        if (searchQuery.isNotBlank()) {
            StorageFilterHelper.searchTree(rootNode, searchQuery)
        } else {
            emptyList()
        }
    }

    // Single LazyColumn wrapping entire screen
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Top search anchor (pinned / stickyHeader)
        stickyHeader {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(56.dp)
                        .clickable { isSearchActive = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialSymbol(
                            name = "search",
                            active = true,
                            size = 22.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Search files, extensions or >1GB...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        MaterialSymbol(
                            name = "tune",
                            active = true,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 2. Primary storage consumer card (Asymmetrical tertiaryContainer)
        item {
            Card(
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                    bottomStart = 28.dp,
                    bottomEnd = 4.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = primaryCategory.name.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialSymbol(
                                    name = "analytics",
                                    active = true,
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = FileUtils.formatFileSize(primaryCategory.totalSize),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${primaryCategory.fileCount} items consuming storage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.80f)
                        )
                        Button(
                            onClick = {
                                val sampleChild = rootNode.children?.firstOrNull { it.name.contains(primaryCategory.samplePath, ignoreCase = true) }
                                if (sampleChild != null) {
                                    onNodeClick(sampleChild, sampleChild.name)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Review Folder",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 3. Filter button group (Connected capsule with shared inner dividers)
        item {
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
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 4. Largest files horizontal gallery (Multi-browse horizontal carousel)
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
                    items(displayedFiles) { entry ->
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
                            modifier = Modifier
                                .width(160.dp)
                                .height(220.dp)
                                .clickable { onNodeClick(entry.node, entry.path) }
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Top 2/3: Media thumbnail / centered file type icon
                                Surface(
                                    color = iconColor.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(2f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Surface(
                                            shape = CircleShape,
                                            color = iconColor.copy(alpha = 0.20f),
                                            modifier = Modifier.size(56.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = FileUtils.getNodeIcon(entry.node, false),
                                                    contentDescription = null,
                                                    tint = iconColor,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Bottom 1/3: Filename & exact file size
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
                                        fontFamily = FontFamily.Monospace,
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
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 5. Heavy apps vertical list
        item {
            Text(
                text = "Heavy Applications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        itemsIndexed(
            items = heavyApps,
            key = { index, app -> "${app.name}_$index" }
        ) { _, app ->
            val pkgName = remember(app) { FileUtils.extractPackageName(app) }
            val appChildren = app.children
            val cacheSize = appChildren?.firstOrNull { it.name == "Cache" }?.size ?: 0L
            val codeSize = appChildren?.firstOrNull { it.name.startsWith("App Code") }?.size ?: 0L

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
                    // Left Zone: 48dp app icon + label + cache size
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

                    // Center Zone: Thin vertical divider line
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Right Zone: Expressive split button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Main side: App Info in Settings
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                bottomStart = 12.dp,
                                topEnd = 4.dp,
                                bottomEnd = 4.dp
                            ),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .height(38.dp)
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
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = "Info",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Trailing side: Uninstall prompt
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 4.dp,
                                bottomStart = 4.dp,
                                topEnd = 12.dp,
                                bottomEnd = 12.dp
                            ),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .size(38.dp)
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

    // M3 Full Screen Search Dialog
    if (isSearchActive) {
        Dialog(
            onDismissRequest = {
                isSearchActive = false
                searchQuery = ""
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val keyboardController = LocalSoftwareKeyboardController.current

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search TopBar
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isSearchActive = false
                                    searchQuery = ""
                                }
                            ) {
                                MaterialSymbol(
                                    name = "arrow_back",
                                    active = true,
                                    size = 22.dp,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text("Type extension (e.g. .mp4), name, or >500M...")
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    keyboardController?.hide()
                                }),
                                modifier = Modifier.weight(1f)
                            )

                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    MaterialSymbol(
                                        name = "close",
                                        active = true,
                                        size = 20.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Results
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (searchQuery.isBlank()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Search Storage",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Filter by extension (e.g. .apk, .mp4, .pdf), filename, or size thresholds (>100MB, >1GB)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (searchResults.isEmpty()) {
                            item {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp)
                                ) {
                                    Text(
                                        text = "No matching files found for \"$searchQuery\"",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(searchResults) { _, entry ->
                                ListItem(
                                    leadingContent = {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = FileUtils.getNodeIcon(entry.node, false),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    },
                                    content = {
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
                                        isSearchActive = false
                                        onNodeClick(entry.node, entry.path)
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            }
                        }
                    }
                }
            }
        }
    }
}
