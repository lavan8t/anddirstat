package com.kd.anddirstat.ui.screens.cleaners

import android.os.Environment
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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

enum class ScreenshotSort(override val label: String, override val icon: String) : SortOption {
    NEWEST("Newest First", "schedule"),
    OLDEST("Oldest First", "history"),
    LARGEST("Largest First", "folder_zip")
}

data class ScreenshotItem(
    val file: File,
    val name: String,
    val size: Long,
    val path: String
)

@Composable
fun ScreenshotsCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(true) }
    var screenshots by remember { mutableStateOf<List<ScreenshotItem>>(emptyList()) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sortOrder by remember { mutableStateOf(ScreenshotSort.NEWEST) }
    var isGridView by remember { mutableStateOf(true) }

    var isDeleting by remember { mutableStateOf(false) }
    var deleteCurrentCount by remember { mutableIntStateOf(0) }
    var deleteTotalCount by remember { mutableIntStateOf(0) }
    var deleteCurrentFileName by remember { mutableStateOf("") }

    fun sortScreenshotList(list: List<ScreenshotItem>, sort: ScreenshotSort): List<ScreenshotItem> {
        return when (sort) {
            ScreenshotSort.NEWEST -> list.sortedByDescending { it.file.lastModified() }
            ScreenshotSort.OLDEST -> list.sortedBy { it.file.lastModified() }
            ScreenshotSort.LARGEST -> list.sortedByDescending { it.size }
        }
    }

    fun scanScreenshots() {
        scope.launch {
            isScanning = true
            val results = withContext(Dispatchers.IO) {
                val found = mutableListOf<ScreenshotItem>()
                val root = Environment.getExternalStorageDirectory()
                val targetDirs = listOf(
                    File(root, "Pictures/Screenshots"),
                    File(root, "DCIM/Screenshots"),
                    File(root, "Pictures/Screen recordings"),
                    File(root, "DCIM/Screen recordings")
                )

                for (dir in targetDirs) {
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEach { f ->
                            found.add(ScreenshotItem(file = f, name = f.name, size = f.length(), path = f.absolutePath))
                        }
                    }
                }

                // Also scan general Pictures / DCIM for files with "screenshot" in name
                listOf(File(root, "Pictures"), File(root, "DCIM")).forEach { parent ->
                    if (parent.exists() && parent.isDirectory) {
                        parent.listFiles()?.filter { it.isFile && it.name.contains("screenshot", ignoreCase = true) }?.forEach { f ->
                            found.add(ScreenshotItem(file = f, name = f.name, size = f.length(), path = f.absolutePath))
                        }
                    }
                }

                sortScreenshotList(found.distinctBy { it.path }, sortOrder)
            }

            screenshots = results
            selectedPaths = emptySet()
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        scanScreenshots()
    }

    fun handleDeleteSelected() {
        val toDelete = screenshots.filter { selectedPaths.contains(it.path) }.map { it.file }
        if (toDelete.isEmpty()) return

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            isDeleting = true
            FileMaintenanceEngine.batchDelete(
                context = context,
                files = toDelete,
                isPermanent = false,
                actionName = "Moved to Recycle Bin"
            ) { curr, tot, name ->
                deleteCurrentCount = curr
                deleteTotalCount = tot
                deleteCurrentFileName = name
            }
            isDeleting = false
            scanScreenshots()
        }
    }

    val totalSelectedSize = remember(screenshots, selectedPaths) {
        screenshots.filter { selectedPaths.contains(it.path) }.sumOf { it.size }
    }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    CleanerScaffold(
        title = "Screenshots",
        selectedCount = selectedPaths.size,
        totalCount = screenshots.size,
        isLoading = isScanning,
        isOperating = isDeleting,
        operationProgress = Triple(deleteCurrentCount, deleteTotalCount, deleteCurrentFileName),
        operationIsTrash = true,
        onBack = onBack,
        onSelectAllToggle = {
            selectedPaths = if (selectedPaths.size == screenshots.size) emptySet() else screenshots.map { it.path }.toSet()
        },
        actions = {
            if (!isScanning && screenshots.isNotEmpty()) {
                CleanerSortDropdown(
                    options = ScreenshotSort.entries.toTypedArray(),
                    selected = sortOrder,
                    onSelect = {
                        sortOrder = it
                        screenshots = sortScreenshotList(screenshots, it)
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
            if (!isScanning && screenshots.isNotEmpty()) {
                CleanerBottomActionBar(
                    visible = true,
                    enabled = selectedPaths.isNotEmpty(),
                    actionText = if (selectedPaths.isNotEmpty()) "Clean ${FileUtils.formatFileSize(totalSelectedSize)} (${selectedPaths.size})" else "Clean",
                    actionIcon = "delete",
                    isDestructive = true,
                    onClick = { handleDeleteSelected() }
                )
            }
        }
    ) {
        when {
            isScanning -> CleanerLoadingState("Scanning for screenshots...")
            screenshots.isEmpty() -> CleanerEmptyState(
                icon = "camera_alt",
                title = "No Screenshots Found",
                subtitle = "Your storage has no screenshots or screen recordings to clean.",
                onButtonClick = onBack
            )
            isGridView -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(screenshots, key = { it.path }) { item ->
                        val isSelected = selectedPaths.contains(item.path)
                        val isStarred = FavoritesManager.isStarred(context, item.path)

                        Card(
                            onClick = {
                                selectedPaths = if (isSelected) selectedPaths - item.path else selectedPaths + item.path
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                MediaThumbnailView(
                                    path = item.path,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedPaths = if (checked) selectedPaths + item.path else selectedPaths - item.path
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                }

                                if (isStarred) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                    ) {
                                        MaterialSymbol("star", active = true, size = 18.dp, tint = MaterialTheme.colorScheme.error)
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = FileUtils.formatFileSize(item.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
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
                    items(screenshots, key = { it.path }) { item ->
                        val isSelected = selectedPaths.contains(item.path)
                        val isStarred = FavoritesManager.isStarred(context, item.path)

                        Card(
                            onClick = {
                                selectedPaths = if (isSelected) selectedPaths - item.path else selectedPaths + item.path
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
                                        selectedPaths = if (checked) selectedPaths + item.path else selectedPaths - item.path
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                MediaThumbnailView(
                                    path = item.path,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isStarred) {
                                            MaterialSymbol("star", active = true, size = 16.dp, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Text(
                                        text = "${FileUtils.formatFileSize(item.size)} • ${dateFormat.format(Date(item.file.lastModified()))}",
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
