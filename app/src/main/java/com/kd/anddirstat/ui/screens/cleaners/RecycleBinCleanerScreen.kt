package com.kd.anddirstat.ui.screens.cleaners

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.viewmodel.AndroidCleanerDeleter
import com.kd.anddirstat.viewmodel.AndroidCleanerHaptics
import com.kd.anddirstat.viewmodel.AndroidCleanerNotifier
import com.kd.anddirstat.viewmodel.AndroidTrashRestorer
import com.kd.anddirstat.viewmodel.AndroidTrashSource
import com.kd.anddirstat.viewmodel.CleanerViewModelFactory
import com.kd.anddirstat.viewmodel.RecycleBinViewModel
import com.kd.anddirstat.viewmodel.TrashSort
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecycleBinCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val appContext = context.applicationContext

    val vm: RecycleBinViewModel = viewModel(
        factory = CleanerViewModelFactory {
            RecycleBinViewModel(
                source = AndroidTrashSource(),
                deleter = AndroidCleanerDeleter(appContext),
                restorer = AndroidTrashRestorer(appContext),
                haptics = AndroidCleanerHaptics(appContext, haptic),
                notifier = AndroidCleanerNotifier()
            )
        }
    )
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.onScan() }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }
    val selectedFiles = uiState.selection.selectedPaths
    val totalSelectedBytes = remember(uiState.items, selectedFiles) {
        uiState.items.filter { it.path in selectedFiles }.sumOf { it.size }
    }

    CleanerScaffold(
        title = "Recycle Bin",
        selectedCount = selectedFiles.size,
        totalCount = uiState.items.size,
        isLoading = uiState.isLoading,
        isOperating = uiState.isOperating,
        operationProgress = Triple(uiState.progress.current, uiState.progress.total, uiState.progress.fileName),
        operationIsTrash = uiState.operationIsTrash,
        onBack = onBack,
        onSelectAllToggle = { vm.onSelectAllToggle() },
        actions = {
            if (!uiState.isLoading && uiState.items.isNotEmpty()) {
                CleanerSortDropdown(
                    options = TrashSort.entries.toTypedArray(),
                    selected = uiState.selection.sortOrder,
                    onSelect = { vm.onSortChanged(it) }
                )
                IconButton(onClick = { vm.onToggleGrid() }) {
                    MaterialSymbol(name = if (uiState.isGridView) "view_list" else "grid_view", active = true)
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
                            onClick = { vm.onRestoreSelected() },
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
                            onClick = { vm.onDeleteSelected() },
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
            uiState.isLoading -> CleanerLoadingState("Scanning recycle bin...")
            uiState.items.isEmpty() -> CleanerEmptyState(
                icon = "delete_outline",
                title = "Recycle Bin is Empty",
                subtitle = "Deleted files moved to the recycle bin will appear here.",
                onButtonClick = onBack
            )
            uiState.isGridView -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.items, key = { it.path }) { item ->
                        val isSelected = selectedFiles.contains(item.path)
                        Card(
                            onClick = { vm.onToggleSelected(item.path) },
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
                                        onCheckedChange = { vm.onToggleSelected(item.path) },
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
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(uiState.items, key = { _, it -> it.path }) { index, item ->
                        val isSelected = selectedFiles.contains(item.path)
                        val itemShape = getGroupedItemShape(index, uiState.items.size, outerRadius = 20.dp, innerRadius = 4.dp)
                        Card(
                            onClick = { vm.onToggleSelected(item.path) },
                            shape = itemShape,
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
                                    onCheckedChange = { vm.onToggleSelected(item.path) },
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
