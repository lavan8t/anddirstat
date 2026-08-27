package com.kd.anddirstat.ui.screens

import android.os.Environment
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.AppTooltip
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class ExplorerTreeRow(
    val node: CompactNode,
    val path: String,
    val depth: Int,
    val isExpanded: Boolean,
    val hasChildren: Boolean,
    val parentSize: Long
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerView(
    rootNode: CompactNode,
    onNodeClick: (CompactNode, String) -> Unit,
    onNodesDeleted: (Set<CompactNode>) -> Unit = {},
    onRefresh: () -> Unit = {},
    onNavigateTo: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    var expandedNodes by remember(rootNode) { mutableStateOf(setOf(rootNode)) }
    var selectedRows by remember(rootNode) { mutableStateOf(mapOf<CompactNode, String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var isStarredExpanded by remember { mutableStateOf(false) }
    var starredPaths by remember(rootNode) { mutableStateOf(FavoritesManager.getStarredPaths(context)) }
    var trashedCount by remember { mutableIntStateOf(0) }
    var trashedSize by remember { mutableLongStateOf(0L) }

    val starredEntries = remember(rootNode, starredPaths, isStarredExpanded) {
        if (!isStarredExpanded || starredPaths.isEmpty()) emptyList()
        else {
            val list = mutableListOf<TopFileEntry>()
            fun collect(node: CompactNode, path: String) {
                val fullPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                if (starredPaths.contains(fullPath) || FavoritesManager.isStarred(context, fullPath)) {
                    list.add(TopFileEntry(node, fullPath))
                }
                node.children?.forEach { collect(it, fullPath) }
            }
            collect(rootNode, "")
            list.distinctBy { it.path }.sortedByDescending { it.node.size }
        }
    }

    LaunchedEffect(rootNode) {
        withContext(Dispatchers.IO) {
            var count = 0
            var size = 0L
            fun scanDir(dir: File, depth: Int = 0) {
                if (depth > 5 || !dir.exists() || !dir.canRead()) return
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (f.name.startsWith(".trashed") || dir.name.startsWith(".trashed")) {
                        count++
                        size += if (f.isDirectory) FileUtils.getFolderSize(f) else f.length()
                    } else if (f.isDirectory && !f.name.equals("android", ignoreCase = true) && !f.name.startsWith(".")) {
                        scanDir(f, depth + 1)
                    }
                }
            }
            val rootStorage = Environment.getExternalStorageDirectory()
            if (rootStorage != null && rootStorage.exists()) {
                scanDir(rootStorage)
            }
            trashedCount = count
            trashedSize = size
        }
    }

    var isDeleting by remember { mutableStateOf(false) }
    var deleteCurrentCount by remember { mutableStateOf(0) }
    var deleteTotalCount by remember { mutableStateOf(0) }
    var deleteCurrentFileName by remember { mutableStateOf("") }
    var deleteIsTrash by remember { mutableStateOf(true) }

    fun flattenTree(
        parent: CompactNode,
        parentPath: String,
        depth: Int,
        outList: ArrayList<ExplorerTreeRow>
    ) {
        val children = parent.children ?: return
        val valid = children.filter { it.size > 0L && !it.name.startsWith(".trashed") && it.name != "[Recycle Bin]" }.sortedByDescending { it.size }
        for (child in valid) {
            val childPath = if (parentPath == "Device Storage") child.name
                else if (parentPath.endsWith("/")) "$parentPath${child.name}"
                else "$parentPath/${child.name}"
            val isApp = child.children?.any { it.name.startsWith("App Code") } == true
            val isDirWithChildren = child.isDirectory && !isApp && child.children?.any { it.size > 0L } == true
            val isExpanded = expandedNodes.contains(child)
            outList.add(ExplorerTreeRow(child, childPath, depth, isExpanded, isDirWithChildren, parent.size))
            if (isDirWithChildren && isExpanded) {
                flattenTree(child, childPath, depth + 1, outList)
            }
        }
    }

    val visibleRows = remember(rootNode, expandedNodes) {
        val list = ArrayList<ExplorerTreeRow>(128)
        flattenTree(rootNode, rootNode.name, 0, list)
        list
    }

    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var isPredictiveBackActive by remember { mutableStateOf(false) }
    var predictiveBackSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    PredictiveBackHandler(enabled = selectedRows.isNotEmpty()) { progress ->
        try {
            isPredictiveBackActive = true
            progress.collect { backEvent ->
                predictiveBackSwipeEdge = backEvent.swipeEdge
                predictiveBackProgress = backEvent.progress
            }
            selectedRows = emptyMap()
        } catch (_: kotlinx.coroutines.CancellationException) {
        } finally {
            isPredictiveBackActive = false
            predictiveBackProgress = 0f
        }
    }

    val slideDirection = if (predictiveBackSwipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
    val slideOffsetX = if (isPredictiveBackActive) (predictiveBackProgress * 72f * slideDirection) else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = with(density) { slideOffsetX.dp.toPx() }
                scaleX = if (isPredictiveBackActive) 1f - (predictiveBackProgress * 0.06f) else 1f
                scaleY = if (isPredictiveBackActive) 1f - (predictiveBackProgress * 0.06f) else 1f
                alpha = if (isPredictiveBackActive) 1f - (predictiveBackProgress * 0.15f) else 1f
            }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Starred and Recycle Bin 2-Column Tools Row
            item(key = "virtual_folders_tools_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tool 1: Starred Files
                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateTo?.invoke(com.kd.anddirstat.model.AppDestinations.STARRED)
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
                                name = "star",
                                active = true,
                                size = 28.dp,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Starred",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (starredEntries.isEmpty()) "0 items" else "${starredEntries.size} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Tool 2: Recycle Bin
                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateTo?.invoke(com.kd.anddirstat.model.AppDestinations.CLEANER_RECYCLE_BIN)
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
                            Column {
                                Text(
                                    text = "Recycle Bin",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (trashedCount == 0) "Empty" else "$trashedCount items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item(key = "virtual_folders_divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }

            itemsIndexed(
                items = visibleRows,
                key = { index, row -> "${row.path}_${row.node.name}_${row.depth}_$index" }
            ) { _, row ->
                val child = row.node
                val fraction = if (row.parentSize > 0L) (child.size.toDouble() / row.parentSize.toDouble()).coerceIn(0.0, 1.0) else 0.0
                val childColor = remember(child, isDark) { FileUtils.getNodeIconColor(child, isDark) }
                val isApp = remember(child) { child.children?.any { it.name.startsWith("App Code") } == true }
                val appPkg = remember(child, isApp, row.path) { if (isApp) FileUtils.extractPackageName(child, row.path, context) else null }
                val isMedia = remember(child.name, child.isDirectory) {
                    if (child.isDirectory) false
                    else {
                        val l = child.name.lowercase()
                        l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") ||
                        l.endsWith(".heic") || l.endsWith(".gif") || l.endsWith(".mp4") || l.endsWith(".mkv") ||
                        l.endsWith(".avi") || l.endsWith(".mov") || l.endsWith(".webm") || l.endsWith(".3gp") ||
                        l.endsWith(".apk")
                    }
                }
                val isSelectable = remember(child) {
                    !child.isDirectory
                }
                val isSelected = isSelectable && selectedRows.containsKey(child)

                ListItem(
                    leadingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (row.depth > 0) {
                                Spacer(modifier = Modifier.width((row.depth * 18).dp))
                            }

                            if (row.hasChildren) {
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (row.isExpanded) 90f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "explorerChevronRotation"
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            expandedNodes = if (row.isExpanded) expandedNodes - child else expandedNodes + child
                                        }
                                ) {
                                    MaterialSymbol(
                                        name = "chevron_right",
                                        active = true,
                                        size = 22.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                                    )
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (isSelectable) {
                                            Modifier.clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedRows = if (isSelected) selectedRows - child else selectedRows + (child to row.path)
                                            }
                                        } else Modifier
                                    )
                            ) {
                                if (isMedia) {
                                    MediaThumbnailView(
                                        node = child,
                                        path = row.path,
                                        fallbackTint = childColor,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else if (appPkg != null) {
                                    AppIconView(
                                        packageName = appPkg,
                                        contentDescription = child.name,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    val symbolName = remember(child, isApp) { FileUtils.getNodeSymbolName(child, isApp) }
                                    MaterialSymbol(
                                        name = symbolName,
                                        active = true,
                                        size = 26.dp,
                                        tint = childColor
                                    )
                                }
                            }
                        }
                    },
                    headlineContent = {
                        val cleanTitle = remember(child.name) { FileUtils.cleanDisplayName(child.name) }
                        Text(
                            text = cleanTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (child.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = FileUtils.formatFileSize(child.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", fraction * 100.0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { fraction.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = childColor,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selectedRows.isNotEmpty() && isSelectable) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedRows = if (isSelected) selectedRows - child else selectedRows + (child to row.path)
                            } else {
                                if (row.hasChildren) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    expandedNodes = if (row.isExpanded) expandedNodes - child else expandedNodes + child
                                } else {
                                    onNodeClick(child, row.path)
                                }
                            }
                        },
                        onLongClick = {
                            if (row.hasChildren) {
                                onNodeClick(child, row.path)
                            } else if (isSelectable) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedRows = if (isSelected) selectedRows - child else selectedRows + (child to row.path)
                            }
                        }
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            }
        }

        // Floating Selection Bar (Rich, expressive, sitting right above bottom navbar)
        AnimatedVisibility(
            visible = selectedRows.isNotEmpty(),
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
                                        selectedRows = emptyMap()
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
                                text = "${selectedRows.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = FileUtils.formatFileSize(selectedRows.keys.sumOf { it.size }, context),
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

        if (showDeleteDialog && selectedRows.isNotEmpty()) {
            val count = selectedRows.size
            val totalBytes = selectedRows.keys.sumOf { it.size }
            val hasApps = selectedRows.any { (node, path) -> FileUtils.extractPackageName(node, path, context) != null }
            val allApps = selectedRows.all { (node, path) -> FileUtils.extractPackageName(node, path, context) != null }
            val allAlreadyTrashed = selectedRows.all { (node, path) -> node.name.startsWith(".trashed") || path.contains(".trashed") || path.contains("[Recycle Bin]") }

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
                            val itemsToDelete = selectedRows.toList()
                            val (starredItems, unstarredItems) = itemsToDelete.partition { (node, path) ->
                                com.kd.anddirstat.util.FavoritesManager.isStarred(context, path)
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
                                    unstarredItems.forEachIndexed { index, (node, path) ->
                                        deleteCurrentCount = index + 1
                                        deleteCurrentFileName = node.name
                                        com.kd.anddirstat.util.AppNotifier.updateProgress(
                                            context = context,
                                            title = if (allAlreadyTrashed) "Deleting permanently..." else "Moving to Recycle Bin...",
                                            detail = "${index + 1} / ${unstarredItems.size}: ${node.name}",
                                            progress = index + 1,
                                            max = unstarredItems.size,
                                            indeterminate = false,
                                            type = "delete"
                                        )
                                        val pkg = FileUtils.extractPackageName(node, path, context)
                                        if (pkg != null) {
                                            packagesToUninstall.add(pkg)
                                        } else {
                                            try {
                                                val f = FileUtils.resolveActualFile(path) ?: FileUtils.resolveActualFile(node.name)
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
                                selectedRows = emptyMap()
                                val baseMsg = if (allAlreadyTrashed) "Deleted $processedCount items permanently" else "Moved $processedCount items to Recycle Bin"
                                val msg = if (starredItems.isNotEmpty()) "$baseMsg (Skipped ${starredItems.size} starred items)" else baseMsg
                                com.kd.anddirstat.util.AppNotifier.finishActivity(context, msg)
                                val deletedNodes = unstarredItems.map { it.first }.toSet()
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
