package com.kd.anddirstat.ui.screens.cleaners

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.viewmodel.AndroidCleanerDeleter
import com.kd.anddirstat.viewmodel.AndroidCleanerHaptics
import com.kd.anddirstat.viewmodel.AndroidDuplicateSource
import com.kd.anddirstat.viewmodel.AutoSelectStrategy
import com.kd.anddirstat.viewmodel.CleanerViewModelFactory
import com.kd.anddirstat.viewmodel.DuplicateSort
import com.kd.anddirstat.viewmodel.DuplicatesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DuplicatesCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val appContext = context.applicationContext

    val vm: DuplicatesViewModel = viewModel(
        factory = CleanerViewModelFactory {
            DuplicatesViewModel(
                source = AndroidDuplicateSource(),
                deleter = AndroidCleanerDeleter(appContext),
                haptics = AndroidCleanerHaptics(appContext, haptic)
            )
        }
    )
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.onScan() }

    var showAutoSelectMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val selectedPaths = uiState.selection.selectedPaths
    val totalSelectedBytes = remember(uiState.groups, selectedPaths) {
        uiState.groups.flatMap { it.files }
            .filter { it.path in selectedPaths }
            .sumOf { it.file.length() }
    }

    CleanerScaffold(
        title = "Duplicate Files",
        selectedCount = selectedPaths.size,
        totalCount = uiState.allSelectableCount,
        isLoading = uiState.isScanning,
        isOperating = uiState.isOperating,
        operationProgress = Triple(uiState.progress.current, uiState.progress.total, uiState.progress.fileName),
        operationIsTrash = true,
        onBack = onBack,
        actions = {
            if (!uiState.isScanning && uiState.groups.isNotEmpty()) {
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
                                vm.onAutoSelect(AutoSelectStrategy.KEEP_NEWEST)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Keep Oldest") },
                            onClick = {
                                showAutoSelectMenu = false
                                vm.onAutoSelect(AutoSelectStrategy.KEEP_OLDEST)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select All Copies") },
                            onClick = {
                                showAutoSelectMenu = false
                                vm.onAutoSelect(AutoSelectStrategy.SELECT_ALL)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Deselect All") },
                            onClick = {
                                showAutoSelectMenu = false
                                vm.onAutoSelect(AutoSelectStrategy.DESELECT_ALL)
                            }
                        )
                    }
                }
                CleanerSortDropdown(
                    options = DuplicateSort.entries.toTypedArray(),
                    selected = uiState.selection.sortOrder,
                    onSelect = { vm.onSortChanged(it) }
                )
            }
        },
        bottomBar = {
            if (!uiState.isScanning && uiState.groups.isNotEmpty()) {
                CleanerBottomActionBar(
                    visible = true,
                    enabled = selectedPaths.isNotEmpty(),
                    actionText = if (selectedPaths.isNotEmpty()) "Clean ${FileUtils.formatFileSize(totalSelectedBytes)} (${selectedPaths.size})" else "Clean",
                    actionIcon = "delete",
                    isDestructive = true,
                    onClick = { vm.onDeleteSelected() }
                )
            }
        }
    ) {
        when {
            uiState.isScanning -> CleanerLoadingState("Scanning storage for duplicate files...")
            uiState.groups.isEmpty() -> CleanerEmptyState(
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
                    items(uiState.groups, key = { it.key }) { group ->
                        val isExpanded = uiState.expandedKeys.contains(group.key)
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
                                        .clickable { vm.onToggleExpanded(group.key) }
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
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        group.files.forEachIndexed { index, fileItem ->
                                            val isSelected = selectedPaths.contains(fileItem.path)
                                            val isStarred = FavoritesManager.isStarred(context, fileItem.path)
                                            val itemShape = getGroupedItemShape(index, group.files.size, outerRadius = 14.dp, innerRadius = 3.dp)

                                            Card(
                                                onClick = { vm.onToggleSelected(fileItem.path) },
                                                shape = itemShape,
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
                                                        onCheckedChange = { vm.onToggleSelected(fileItem.path) },
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
