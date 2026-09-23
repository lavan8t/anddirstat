package com.kd.anddirstat.ui.screens.cleaners

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileMaintenanceEngine
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DuplicateFileItem(
    val file: File,
    val path: String,
    val lastModified: Long
)

data class DuplicateGroup(
    val key: String,
    val fileName: String,
    val fileSize: Long,
    val files: List<DuplicateFileItem>
)

enum class DuplicateSort(override val label: String, override val icon: String) : SortOption {
    LARGEST("Largest Size First", "folder_zip"),
    MOST_COPIES("Most Copies First", "content_copy"),
    NAME_AZ("Name (A to Z)", "sort_by_alpha")
}

@Composable
fun DuplicatesCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(true) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var expandedGroupKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sortOrder by remember { mutableStateOf(DuplicateSort.LARGEST) }
    var showAutoSelectMenu by remember { mutableStateOf(false) }

    var isOperating by remember { mutableStateOf(false) }
    var operationCurrentCount by remember { mutableIntStateOf(0) }
    var operationTotalCount by remember { mutableIntStateOf(0) }
    var operationCurrentFileName by remember { mutableStateOf("") }

    fun sortGroups(groups: List<DuplicateGroup>, sort: DuplicateSort): List<DuplicateGroup> {
        return when (sort) {
            DuplicateSort.LARGEST -> groups.sortedByDescending { it.fileSize * (it.files.size - 1) }
            DuplicateSort.MOST_COPIES -> groups.sortedByDescending { it.files.size }
            DuplicateSort.NAME_AZ -> groups.sortedBy { it.fileName.lowercase() }
        }
    }

    fun scanDuplicates() {
        scope.launch {
            isScanning = true
            val results = withContext(Dispatchers.IO) {
                val sizeMap = mutableMapOf<Long, MutableList<File>>()
                val root = Environment.getExternalStorageDirectory()

                fun walk(dir: File, depth: Int = 0) {
                    if (depth > 8 || !dir.canRead()) return
                    val list = dir.listFiles() ?: return
                    for (f in list) {
                        if (f.isFile && f.length() > 512 * 1024L && !f.name.startsWith(".")) {
                            val listForSize = sizeMap.getOrPut(f.length()) { mutableListOf() }
                            listForSize.add(f)
                        } else if (f.isDirectory && !f.name.equals("Android", ignoreCase = true) && !f.name.startsWith(".")) {
                            walk(f, depth + 1)
                        }
                    }
                }

                if (root != null && root.exists()) {
                    walk(root)
                }

                val groups = mutableListOf<DuplicateGroup>()
                for ((size, files) in sizeMap) {
                    if (files.size >= 2) {
                        // Group by name + size
                        val byName = files.groupBy { it.name }
                        for ((name, matchingFiles) in byName) {
                            if (matchingFiles.size >= 2) {
                                val items = matchingFiles.map {
                                    DuplicateFileItem(
                                        file = it,
                                        path = it.absolutePath,
                                        lastModified = it.lastModified()
                                    )
                                }
                                groups.add(
                                    DuplicateGroup(
                                        key = "${name}_${size}",
                                        fileName = name,
                                        fileSize = size,
                                        files = items
                                    )
                                )
                            }
                        }
                    }
                }
                sortGroups(groups, sortOrder)
            }

            duplicateGroups = results
            expandedGroupKeys = results.map { it.key }.toSet()
            // Default auto-select: keep newest copy, select all older copies
            val autoSelected = mutableSetOf<String>()
            for (group in results) {
                val sorted = group.files.sortedByDescending { it.lastModified }
                if (sorted.size > 1) {
                    sorted.drop(1).forEach { autoSelected.add(it.path) }
                }
            }
            selectedPaths = autoSelected
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        scanDuplicates()
    }

    fun applyAutoSelect(strategy: String) {
        val newSelection = mutableSetOf<String>()
        for (group in duplicateGroups) {
            when (strategy) {
                "keep_newest" -> {
                    val sorted = group.files.sortedByDescending { it.lastModified }
                    sorted.drop(1).forEach { newSelection.add(it.path) }
                }
                "keep_oldest" -> {
                    val sorted = group.files.sortedBy { it.lastModified }
                    sorted.drop(1).forEach { newSelection.add(it.path) }
                }
                "select_all" -> {
                    group.files.forEach { newSelection.add(it.path) }
                }
                "deselect_all" -> {}
            }
        }
        selectedPaths = newSelection
    }

    fun handleDeleteSelected() {
        val toDelete = duplicateGroups.flatMap { it.files }
            .filter { selectedPaths.contains(it.path) }
            .map { it.file }
        if (toDelete.isEmpty()) return

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            isOperating = true
            FileMaintenanceEngine.batchDelete(
                context = context,
                files = toDelete,
                isPermanent = false,
                actionName = "Moved to Recycle Bin"
            ) { curr, tot, name ->
                operationCurrentCount = curr
                operationTotalCount = tot
                operationCurrentFileName = name
            }
            isOperating = false
            scanDuplicates()
        }
    }

    val totalSelectedBytes = remember(duplicateGroups, selectedPaths) {
        duplicateGroups.flatMap { it.files }
            .filter { selectedPaths.contains(it.path) }
            .sumOf { it.file.length() }
    }
    val allSelectableCount = remember(duplicateGroups) {
        duplicateGroups.sumOf { it.files.size }
    }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    CleanerScaffold(
        title = "Duplicate Files",
        selectedCount = selectedPaths.size,
        totalCount = allSelectableCount,
        isLoading = isScanning,
        isOperating = isOperating,
        operationProgress = Triple(operationCurrentCount, operationTotalCount, operationCurrentFileName),
        operationIsTrash = true,
        onBack = onBack,
        actions = {
            if (!isScanning && duplicateGroups.isNotEmpty()) {
                Box {
                    IconButton(onClick = { showAutoSelectMenu = true }) {
                        MaterialSymbol("auto_fix_high", active = true, size = 22.dp)
                    }
                    DropdownMenu(
                        expanded = showAutoSelectMenu,
                        onDismissRequest = { showAutoSelectMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Keep Newest (Recommended)") },
                            onClick = {
                                showAutoSelectMenu = false
                                applyAutoSelect("keep_newest")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Keep Oldest") },
                            onClick = {
                                showAutoSelectMenu = false
                                applyAutoSelect("keep_oldest")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select All Copies") },
                            onClick = {
                                showAutoSelectMenu = false
                                applyAutoSelect("select_all")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Deselect All") },
                            onClick = {
                                showAutoSelectMenu = false
                                applyAutoSelect("deselect_all")
                            }
                        )
                    }
                }
                CleanerSortDropdown(
                    options = DuplicateSort.entries.toTypedArray(),
                    selected = sortOrder,
                    onSelect = {
                        sortOrder = it
                        duplicateGroups = sortGroups(duplicateGroups, it)
                    }
                )
            }
        },
        bottomBar = {
            if (!isScanning && duplicateGroups.isNotEmpty()) {
                CleanerBottomActionBar(
                    visible = true,
                    enabled = selectedPaths.isNotEmpty(),
                    actionText = if (selectedPaths.isNotEmpty()) "Clean ${FileUtils.formatFileSize(totalSelectedBytes)} (${selectedPaths.size})" else "Clean",
                    actionIcon = "delete",
                    isDestructive = true,
                    onClick = { handleDeleteSelected() }
                )
            }
        }
    ) {
        when {
            isScanning -> CleanerLoadingState("Scanning storage for duplicate files...")
            duplicateGroups.isEmpty() -> CleanerEmptyState(
                icon = "check_circle",
                title = "No Duplicates Found",
                subtitle = "Your storage has no identical copies of large files taking up space.",
                onButtonClick = onBack
            )
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(duplicateGroups, key = { it.key }) { group ->
                        val isExpanded = expandedGroupKeys.contains(group.key)
                        val wastedSize = group.fileSize * (group.files.size - 1)

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedGroupKeys = if (isExpanded) expandedGroupKeys - group.key else expandedGroupKeys + group.key
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MediaThumbnailView(
                                        path = group.files.first().path,
                                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group.fileName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${group.files.size} copies • ${FileUtils.formatFileSize(wastedSize)} wasted",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    MaterialSymbol(
                                        name = if (isExpanded) "expand_less" else "expand_more",
                                        active = true,
                                        size = 24.dp
                                    )
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        group.files.forEachIndexed { index, fileItem ->
                                            val isSelected = selectedPaths.contains(fileItem.path)
                                            val isStarred = FavoritesManager.isStarred(context, fileItem.path)

                                            Card(
                                                onClick = {
                                                    selectedPaths = if (isSelected) selectedPaths - fileItem.path else selectedPaths + fileItem.path
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                    else MaterialTheme.colorScheme.surfaceContainer
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = isSelected,
                                                        onCheckedChange = { checked ->
                                                            selectedPaths = if (checked) selectedPaths + fileItem.path else selectedPaths - fileItem.path
                                                        },
                                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = fileItem.path,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${FileUtils.formatFileSize(fileItem.file.length())} • ${dateFormat.format(Date(fileItem.lastModified))}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    if (isStarred) {
                                                        MaterialSymbol("star", active = true, size = 16.dp, tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
