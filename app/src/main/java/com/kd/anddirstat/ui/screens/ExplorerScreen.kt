package com.kd.anddirstat.ui.screens

import android.content.Context
import android.os.Environment
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.GoogleSansFlexFontFamily
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.AppTooltip
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private fun findAncestorNodes(root: CompactNode, target: String): Set<CompactNode> {
    val ancestors = mutableSetOf<CompactNode>()
    val cleanTarget = target.trimEnd('/')
    fun dfs(current: CompactNode, path: String): Boolean {
        val curPath = if (path.isEmpty()) current.name else "$path/${current.name}"
        val cleanCur = curPath.trimEnd('/')
        if (cleanCur == cleanTarget || cleanTarget.endsWith("/$cleanCur") || cleanCur.endsWith("/$cleanTarget")) {
            ancestors.add(current)
            return true
        }
        val couldContain = cleanTarget.startsWith("$cleanCur/") ||
                           cleanTarget.contains("/${current.name}/") ||
                           cleanTarget.endsWith("/${current.name}") ||
                           current == root
        if (couldContain) {
            var found = false
            current.children?.forEach { child ->
                val nextPath = if (current == root && root.name == "Device Storage") "" else cleanCur
                if (dfs(child, nextPath)) {
                    found = true
                }
            }
            if (found) {
                ancestors.add(current)
                return true
            }
        }
        return false
    }
    dfs(root, "")
    return ancestors
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerView(
    rootNode: CompactNode,
    onNodeClick: (CompactNode, String) -> Unit,
    onNodesDeleted: (Set<CompactNode>) -> Unit = {},
    onRefresh: () -> Unit = {},
    onNavigateTo: ((String) -> Unit)? = null,
    onDismissPopup: () -> Unit = {},
    targetPath: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val listState = rememberLazyListState()

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) onDismissPopup()
    }
    val topLevelChildren = remember(rootNode) {
        rootNode.children?.filter { it.size > 0L && !it.name.startsWith(".trashed") && it.name != "[Recycle Bin]" }?.sortedByDescending { it.size } ?: emptyList()
    }
    var expandedNodes by remember(rootNode) { mutableStateOf(setOf(rootNode)) }

    LaunchedEffect(targetPath, rootNode) {
        if (!targetPath.isNullOrBlank()) {
            val ancestors = findAncestorNodes(rootNode, targetPath)
            if (ancestors.isNotEmpty()) {
                expandedNodes = expandedNodes + ancestors
                val topLevelIdx = topLevelChildren.indexOfFirst { ancestors.contains(it) }
                if (topLevelIdx >= 0) {
                    listState.animateScrollToItem((topLevelIdx + 2).coerceAtLeast(0))
                }
            }
        }
    }

    val folderLimits = remember(rootNode) { mutableStateMapOf<String, Int>() }
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
            state = listState,
            contentPadding = PaddingValues(bottom = 116.dp),
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
                items = topLevelChildren,
                key = { index, item -> item.name }
            ) { index, child ->
                val childPath = if (rootNode.name == "Device Storage") child.name else "${rootNode.name}/${child.name}"
                ExplorerNodeItem(
                    node = child,
                    parentPath = childPath,
                    parentSize = rootNode.size,
                    depth = 0,
                    itemIndex = index,
                    isDark = isDark,
                    context = context,
                    haptic = haptic,
                    expandedNodes = expandedNodes,
                    selectedRows = selectedRows,
                    folderLimits = folderLimits,
                    highlightPath = targetPath,
                    onToggleExpand = { node ->
                        expandedNodes = if (expandedNodes.contains(node)) {
                            folderLimits.remove(childPath)
                            expandedNodes - node
                        } else {
                            expandedNodes + node
                        }
                    },
                    onToggleSelect = { node, path ->
                        selectedRows = if (selectedRows.containsKey(node)) selectedRows - node else selectedRows + (node to path)
                    },
                    onNodeClick = onNodeClick
                )
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
                                        AppNotifier.updateProgress(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerNodeItem(
    node: CompactNode,
    parentPath: String,
    parentSize: Long,
    depth: Int,
    itemIndex: Int = 0,
    isDark: Boolean,
    context: Context,
    haptic: HapticFeedback,
    expandedNodes: Set<CompactNode>,
    selectedRows: Map<CompactNode, String>,
    folderLimits: MutableMap<String, Int>,
    highlightPath: String? = null,
    onToggleExpand: (CompactNode) -> Unit,
    onToggleSelect: (CompactNode, String) -> Unit,
    onNodeClick: (CompactNode, String) -> Unit
) {
    val isApp = remember(node) { node.children?.any { it.name.startsWith("App Code") } == true }
    val isDirWithChildren = node.isDirectory && !isApp && node.children?.any { it.size > 0L } == true
    val isExpanded = isDirWithChildren && expandedNodes.contains(node)
    val fraction = if (parentSize > 0L) (node.size.toDouble() / parentSize.toDouble()).coerceIn(0.0, 1.0) else 0.0
    val childColor = remember(node, isDark) { FileUtils.getNodeIconColor(node, isDark) }
    val appPkg = remember(node, isApp, parentPath) { if (isApp) FileUtils.extractPackageName(node, parentPath, context) else null }
    val isMedia = remember(node.name, node.isDirectory) {
        if (node.isDirectory) false
        else {
            val l = node.name.lowercase()
            l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") ||
            l.endsWith(".heic") || l.endsWith(".gif") || l.endsWith(".mp4") || l.endsWith(".mkv") ||
            l.endsWith(".avi") || l.endsWith(".mov") || l.endsWith(".webm") || l.endsWith(".3gp") ||
            l.endsWith(".apk")
        }
    }
    val fullFilePath = remember(parentPath, node.name) {
        if (parentPath.endsWith("/")) "$parentPath${node.name}" else "$parentPath/${node.name}"
    }
    val isHighlighted = remember(highlightPath, parentPath, fullFilePath) {
        highlightPath != null && (
            parentPath == highlightPath ||
            fullFilePath == highlightPath ||
            parentPath.endsWith("/${highlightPath.substringAfterLast('/')}") ||
            fullFilePath.endsWith("/${highlightPath.substringAfterLast('/')}")
        )
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            delay(300)
            bringIntoViewRequester.bringIntoView()
        }
    }
    val isSelectable = remember(node) { !node.isDirectory }
    val isSelected = isSelectable && selectedRows.containsKey(node)

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (depth > 0) {
                        Spacer(modifier = Modifier.width((minOf(depth, 4) * 8).dp))
                    }

                    if (isDirWithChildren) {
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isExpanded) 90f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "explorerChevronRotation"
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleExpand(node)
                                }
                        ) {
                            MaterialSymbol(
                                name = "chevron_right",
                                active = true,
                                size = 16.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(22.dp))
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
                                        onToggleSelect(node, parentPath)
                                    }
                                } else Modifier
                            )
                    ) {
                        if (isMedia) {
                            MediaThumbnailView(
                                node = node,
                                path = fullFilePath,
                                fallbackTint = childColor,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else if (appPkg != null) {
                            AppIconView(
                                packageName = appPkg,
                                contentDescription = node.name,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            val symbolName = remember(node, isApp) { FileUtils.getNodeSymbolName(node, isApp) }
                            MaterialSymbol(
                                name = symbolName,
                                active = true,
                                size = 26.dp,
                                tint = childColor
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                MaterialSymbol(
                                    name = "check",
                                    active = true,
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            },
            headlineContent = {
                val cleanTitle = remember(node.name) { FileUtils.cleanDisplayName(node.name) }
                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
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
                            text = FileUtils.formatFileSize(node.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val displayPercent = fraction * 100.0
                        Text(
                            text = String.format(Locale.US, "%.1f%%", displayPercent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { fraction.toFloat().coerceIn(0f, 1f) },
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
                containerColor = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    isHighlighted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
            ),
            modifier = Modifier
                .bringIntoViewRequester(bringIntoViewRequester)
                .combinedClickable(
                onClick = {
                    if (selectedRows.isNotEmpty() && isSelectable) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleSelect(node, parentPath)
                    } else {
                        if (isDirWithChildren) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleExpand(node)
                        } else {
                            onNodeClick(node, parentPath)
                        }
                    }
                },
                onLongClick = {
                    if (isDirWithChildren) {
                        onNodeClick(node, parentPath)
                    } else if (isSelectable) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleSelect(node, parentPath)
                    }
                }
            )
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

        // Sliding down accordion animation
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val rawChildren = node.children
                val validChildren = remember(node) {
                    rawChildren?.filter { it.size > 0L && !it.name.startsWith(".trashed") && it.name != "[Recycle Bin]" }?.sortedByDescending { it.size } ?: emptyList()
                }
                val limit = folderLimits[parentPath] ?: 30
                val displayChildren = if (validChildren.size > limit) validChildren.take(limit) else validChildren

                displayChildren.forEachIndexed { childIndex, childNode ->
                    val childPath = if (parentPath.endsWith("/")) "$parentPath${childNode.name}" else "$parentPath/${childNode.name}"
                    ExplorerNodeItem(
                        node = childNode,
                        parentPath = childPath,
                        parentSize = node.size,
                        depth = depth + 1,
                        itemIndex = childIndex,
                        isDark = isDark,
                        context = context,
                        haptic = haptic,
                        expandedNodes = expandedNodes,
                        selectedRows = selectedRows,
                        folderLimits = folderLimits,
                        highlightPath = highlightPath,
                        onToggleExpand = onToggleExpand,
                        onToggleSelect = onToggleSelect,
                        onNodeClick = onNodeClick
                    )
                }

                if (validChildren.size > limit) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (16 + (depth + 1) * 14 + 24).dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing $limit of ${validChildren.size} items",
                            fontFamily = GoogleSansFlexFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    folderLimits[parentPath] = limit + 30
                                }
                            ) {
                                Text("+30 more", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    folderLimits[parentPath] = validChildren.size
                                }
                            ) {
                                Text("Show All", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }
            }
        }
    }
}
