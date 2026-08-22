package com.kd.anddirstat.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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

import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoverView(
    rootNode: CompactNode,
    topFiles: List<TopFileEntry>,
    searchQuery: String = "",
    onNodeClick: (CompactNode, String) -> Unit,
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
    var deleteCurrentCount by remember { mutableStateOf(0) }
    var deleteTotalCount by remember { mutableStateOf(0) }
    var deleteCurrentFileName by remember { mutableStateOf("") }
    var deleteIsTrash by remember { mutableStateOf(true) }

    val filterPresets = remember {
        listOf("> 1 GB", "Screenshots", "Duplicates", "Old Downloads", "APKs")
    }

    val rawScreenshots = remember(rootNode) { StorageFilterHelper.getScreenshots(rootNode) }
    var reviewedScreenshots by remember(rootNode) { mutableStateOf(setOf<String>()) }
    val activeScreenshots = remember(rawScreenshots, reviewedScreenshots) {
        rawScreenshots.filter { !reviewedScreenshots.contains(it.path) }
    }
    var showScreenshotsPage by remember { mutableStateOf(false) }

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

            // Screenshots Cleaner Button Banner
            if (rawScreenshots.isNotEmpty()) {
                item(key = "screenshots_cleaner_banner") {
                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showScreenshotsPage = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        MaterialSymbol(
                                            name = "screenshot_monitor",
                                            active = true,
                                            size = 24.dp,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Screenshots Cleaner",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (activeScreenshots.isNotEmpty())
                                            "${activeScreenshots.size} screenshots • ${FileUtils.formatFileSize(activeScreenshots.sumOf { it.node.size })}"
                                        else
                                            "All ${rawScreenshots.size} screenshots reviewed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showScreenshotsPage = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Clean", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
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
                        itemsIndexed(
                            items = displayedFiles,
                            key = { index, entry -> "${entry.path}_$index" }
                        ) { _, entry ->
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
                val pkgName = remember(app) { FileUtils.extractPackageName(app, null, context) }
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
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppTooltip(text = "Clear selection") {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.size(36.dp)
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
                                        size = 18.dp,
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
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AppTooltip(text = "Delete selected") {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showDeleteDialog = true
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
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

        if (showDeleteDialog && selectedEntries.isNotEmpty()) {
            val count = selectedEntries.size
            val totalBytes = selectedEntries.sumOf { it.node.size }
            val hasApps = selectedEntries.any { FileUtils.extractPackageName(it.node, it.path, context) != null }
            val allApps = selectedEntries.all { FileUtils.extractPackageName(it.node, it.path, context) != null }
            val allAlreadyTrashed = selectedEntries.all { it.node.name.startsWith(".trashed") || it.path.contains(".trashed") }

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
                            scope.launch {
                                isDeleting = true
                                deleteTotalCount = itemsToDelete.size
                                deleteIsTrash = !allAlreadyTrashed
                                var processedCount = 0
                                val packagesToUninstall = mutableListOf<String>()

                                withContext(Dispatchers.IO) {
                                    itemsToDelete.forEachIndexed { index, entry ->
                                        deleteCurrentCount = index + 1
                                        deleteCurrentFileName = entry.node.name
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
                                val msg = if (allAlreadyTrashed) "Deleted $processedCount items permanently" else "Moved $processedCount items to Recycle Bin"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onRefresh()
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

        if (showScreenshotsPage) {
            ScreenshotsCleanerPage(
                activeScreenshots = activeScreenshots,
                totalCount = rawScreenshots.size,
                onBack = { showScreenshotsPage = false },
                onTrash = { entry ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val f = FileUtils.resolveActualFile(entry.path) ?: FileUtils.resolveActualFile(entry.node.name)
                                if (f != null && f.exists()) {
                                    FileUtils.deleteOrTrashFile(f, context)
                                }
                            } catch (_: Exception) {}
                        }
                        reviewedScreenshots = reviewedScreenshots + entry.path
                        Toast.makeText(context, "Moved to Recycle Bin", Toast.LENGTH_SHORT).show()
                        onRefresh()
                    }
                },
                onKeep = { entry ->
                    reviewedScreenshots = reviewedScreenshots + entry.path
                },
                onReset = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    reviewedScreenshots = emptySet()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotsCleanerPage(
    activeScreenshots: List<TopFileEntry>,
    totalCount: Int,
    onBack: () -> Unit,
    onTrash: (TopFileEntry) -> Unit,
    onKeep: (TopFileEntry) -> Unit,
    onReset: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Screenshots Cleaner",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeScreenshots.isNotEmpty()) {
                            Text(
                                text = "${activeScreenshots.size} of $totalCount remaining (${FileUtils.formatFileSize(activeScreenshots.sumOf { it.node.size })})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MaterialSymbol("arrow_back", active = true, size = 24.dp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (activeScreenshots.isNotEmpty()) {
                val currentScreenshot = activeScreenshots.first()
                val animOffsetX = remember(currentScreenshot.path) { Animatable(0f) }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .offset { IntOffset(animOffsetX.value.roundToInt(), 0) }
                            .graphicsLayer {
                                rotationZ = (animOffsetX.value / 25f).coerceIn(-12f, 12f)
                            }
                            .pointerInput(currentScreenshot.path) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        scope.launch {
                                            if (animOffsetX.value < -160f) {
                                                animOffsetX.animateTo(-800f, tween(150))
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onTrash(currentScreenshot)
                                            } else if (animOffsetX.value > 160f) {
                                                animOffsetX.animateTo(800f, tween(150))
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onKeep(currentScreenshot)
                                            } else {
                                                animOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            }
                                        }
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        scope.launch {
                                            animOffsetX.snapTo(animOffsetX.value + dragAmount)
                                        }
                                    }
                                )
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MediaThumbnailView(
                                node = currentScreenshot.node,
                                path = currentScreenshot.path,
                                fallbackTint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Dynamic Left (Trash) Overlay
                            val leftAlpha = (-animOffsetX.value / 150f).coerceIn(0f, 0.85f)
                            if (leftAlpha > 0.05f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = leftAlpha)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MaterialSymbol(name = "delete", active = true, size = 44.dp, tint = MaterialTheme.colorScheme.onError)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("TRASH", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onError)
                                    }
                                }
                            }

                            // Dynamic Right (Keep) Overlay
                            val rightAlpha = (animOffsetX.value / 150f).coerceIn(0f, 0.85f)
                            if (rightAlpha > 0.05f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = rightAlpha)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MaterialSymbol(name = "check", active = true, size = 44.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("KEEP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }

                            // Bottom file metadata info
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = currentScreenshot.node.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${FileUtils.formatFileSize(currentScreenshot.node.size)} • ${currentScreenshot.path}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "← Swipe left to trash  •  Swipe right to keep →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    animOffsetX.animateTo(-800f, tween(150))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTrash(currentScreenshot)
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            MaterialSymbol("delete", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Trash", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    animOffsetX.animateTo(800f, tween(150))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKeep(currentScreenshot)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            MaterialSymbol("check", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Keep", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    MaterialSymbol(
                        name = "check_circle",
                        active = true,
                        size = 64.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "All Caught Up!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You've reviewed all $totalCount screenshots on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onReset) {
                            Text("Review again", fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = onBack) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
