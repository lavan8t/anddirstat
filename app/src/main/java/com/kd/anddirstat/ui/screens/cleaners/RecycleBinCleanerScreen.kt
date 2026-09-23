package com.kd.anddirstat.ui.screens.cleaners

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FileMaintenanceEngine
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrashedItem(
    val file: File,
    val cleanName: String,
    val rawName: String,
    val size: Long,
    val lastModified: Long,
    val path: String
)

enum class TrashSort(override val label: String, override val icon: String) : SortOption {
    NEWEST("Newest First", "schedule"),
    OLDEST("Oldest First", "history"),
    LARGEST("Largest First", "folder_zip"),
    SMALLEST("Smallest First", "sort"),
    NAME_AZ("Name (A-Z)", "sort_by_alpha")
}

@Composable
fun RecycleBinCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var isGridView by remember { mutableStateOf(false) }
    var trashedFiles by remember { mutableStateOf<List<TrashedItem>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sortOrder by remember { mutableStateOf(TrashSort.NEWEST) }

    var isOperating by remember { mutableStateOf(false) }
    var operationCurrentCount by remember { mutableIntStateOf(0) }
    var operationTotalCount by remember { mutableIntStateOf(0) }
    var operationCurrentFileName by remember { mutableStateOf("") }
    var operationIsTrash by remember { mutableStateOf(false) }

    fun getCleanFileName(rawName: String): String {
        var clean = rawName.removePrefix(".trashed-")
        if (clean.contains("-") && clean.substringBefore("-").all { it.isDigit() }) {
            clean = clean.substringAfter("-")
        }
        return clean.ifEmpty { rawName }
    }

    fun applySort(list: List<TrashedItem>, sort: TrashSort): List<TrashedItem> {
        return when (sort) {
            TrashSort.NEWEST -> list.sortedByDescending { it.lastModified }
            TrashSort.OLDEST -> list.sortedBy { it.lastModified }
            TrashSort.LARGEST -> list.sortedByDescending { it.size }
            TrashSort.SMALLEST -> list.sortedBy { it.size }
            TrashSort.NAME_AZ -> list.sortedBy { it.cleanName.lowercase() }
        }
    }

    fun scanTrashedItems() {
        scope.launch {
            isLoading = true
            val results = withContext(Dispatchers.IO) {
                val found = mutableListOf<TrashedItem>()
                val root = Environment.getExternalStorageDirectory()
                if (root != null && root.exists()) {
                    fun walk(dir: File, depth: Int = 0) {
                        if (depth > 8 || !dir.canRead()) return
                        val list = dir.listFiles() ?: return
                        for (f in list) {
                            if (f.name.startsWith(".trashed-") || f.name.equals(".trash", ignoreCase = true) || f.name.equals(".trashed", ignoreCase = true)) {
                                found.add(
                                    TrashedItem(
                                        file = f,
                                        cleanName = getCleanFileName(f.name),
                                        rawName = f.name,
                                        size = if (f.isDirectory) FileUtils.getFolderSize(f) else f.length(),
                                        lastModified = f.lastModified(),
                                        path = f.absolutePath
                                    )
                                )
                            } else if (f.isDirectory && !f.name.equals("Android", ignoreCase = true)) {
                                walk(f, depth + 1)
                            }
                        }
                    }
                    walk(root)
                }
                applySort(found.distinctBy { it.path }, sortOrder)
            }
            trashedFiles = results
            selectedFiles = emptySet()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        scanTrashedItems()
    }

    fun handleDeleteSelected() {
        val toDelete = trashedFiles.filter { selectedFiles.contains(it.path) }.map { it.file }
        if (toDelete.isEmpty()) return

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            isOperating = true
            operationIsTrash = false
            FileMaintenanceEngine.batchDelete(
                context = context,
                files = toDelete,
                isPermanent = true,
                actionName = "Permanently deleted"
            ) { curr, tot, name ->
                operationCurrentCount = curr
                operationTotalCount = tot
                operationCurrentFileName = name
            }
            isOperating = false
            scanTrashedItems()
        }
    }

    fun handleRestoreSelected() {
        val toRestore = trashedFiles.filter { selectedFiles.contains(it.path) }
        if (toRestore.isEmpty()) return

        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            isOperating = true
            operationTotalCount = toRestore.size
            operationIsTrash = true
            var restoredCount = 0

            withContext(Dispatchers.IO) {
                toRestore.forEachIndexed { index, item ->
                    operationCurrentCount = index + 1
                    operationCurrentFileName = item.cleanName
                    try {
                        if (FileUtils.restoreTrashedFile(item.file, context)) {
                            restoredCount++
                        }
                    } catch (_: Exception) {}
                }
            }

            isOperating = false
            AppNotifier.notify("Restored $restoredCount items")
            scanTrashedItems()
        }
    }

    val totalSelectedBytes = remember(trashedFiles, selectedFiles) {
        trashedFiles.filter { selectedFiles.contains(it.path) }.sumOf { it.size }
    }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }

    CleanerScaffold(
        title = "Recycle Bin",
        selectedCount = selectedFiles.size,
        totalCount = trashedFiles.size,
        isLoading = isLoading,
        isOperating = isOperating,
        operationProgress = Triple(operationCurrentCount, operationTotalCount, operationCurrentFileName),
        operationIsTrash = operationIsTrash,
        onBack = onBack,
        onSelectAllToggle = {
            selectedFiles = if (selectedFiles.size == trashedFiles.size) emptySet() else trashedFiles.map { it.path }.toSet()
        },
        actions = {
            if (!isLoading && trashedFiles.isNotEmpty()) {
                CleanerSortDropdown(
                    options = TrashSort.entries.toTypedArray(),
                    selected = sortOrder,
                    onSelect = {
                        sortOrder = it
                        trashedFiles = applySort(trashedFiles, it)
                    }
                )
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isGridView = !isGridView
                }) {
                    MaterialSymbol(name = if (isGridView) "view_list" else "grid_view", active = true)
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedFiles.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { handleRestoreSelected() },
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MaterialSymbol("restore_from_trash", active = true, size = 20.dp)
                                Text("Restore (${selectedFiles.size})", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { handleDeleteSelected() },
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MaterialSymbol("delete_forever", active = true, size = 20.dp)
                                Text("Delete", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) {
        when {
            isLoading -> CleanerLoadingState("Scanning recycle bin...")
            trashedFiles.isEmpty() -> CleanerEmptyState(
                icon = "delete_outline",
                title = "Recycle Bin is Empty",
                subtitle = "Deleted files moved to the recycle bin will appear here.",
                onButtonClick = onBack
            )
            isGridView -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trashedFiles, key = { it.path }) { item ->
                        val isSelected = selectedFiles.contains(item.path)
                        Card(
                            onClick = {
                                selectedFiles = if (isSelected) selectedFiles - item.path else selectedFiles + item.path
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.file.isDirectory) {
                                        MaterialSymbol("folder", active = true, size = 32.dp, tint = MaterialTheme.colorScheme.primary)
                                    } else {
                                        MediaThumbnailView(
                                            path = item.path,
                                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedFiles = if (checked) selectedFiles + item.path else selectedFiles - item.path
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Column {
                                    Text(
                                        text = item.cleanName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = FileUtils.formatFileSize(item.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trashedFiles, key = { it.path }) { item ->
                        val isSelected = selectedFiles.contains(item.path)
                        Card(
                            onClick = {
                                selectedFiles = if (isSelected) selectedFiles - item.path else selectedFiles + item.path
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedFiles = if (checked) selectedFiles + item.path else selectedFiles - item.path
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                if (item.file.isDirectory) {
                                    MaterialSymbol("folder", active = true, size = 32.dp, tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    MediaThumbnailView(
                                        path = item.path,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.cleanName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${FileUtils.formatFileSize(item.size)} • ${dateFormat.format(Date(item.lastModified))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
