package com.kd.anddirstat.ui.screens.cleaners

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.viewmodel.AndroidCleanerDeleter
import com.kd.anddirstat.viewmodel.AndroidEmptyFolderSource
import com.kd.anddirstat.viewmodel.CleanerViewModelFactory
import com.kd.anddirstat.viewmodel.EmptyFoldersViewModel

@Composable
fun EmptyFoldersCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val vm: EmptyFoldersViewModel = viewModel(
        factory = CleanerViewModelFactory {
            EmptyFoldersViewModel(
                source = AndroidEmptyFolderSource(),
                deleter = AndroidCleanerDeleter(appContext)
            )
        }
    )
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.onScan() }

    val selectedFolders = uiState.selection.selectedPaths

    CleanerScaffold(
        title = "Empty Folders",
        selectedCount = selectedFolders.size,
        totalCount = uiState.folders.size,
        isLoading = uiState.isScanning,
        isOperating = uiState.isOperating,
        operationProgress = Triple(uiState.progress.current, uiState.progress.total, uiState.progress.fileName),
        onBack = onBack,
        onSelectAllToggle = { vm.onSelectAllToggle() },
        bottomBar = {
            if (!uiState.isScanning && uiState.folders.isNotEmpty()) {
                CleanerBottomActionBar(
                    visible = true,
                    enabled = selectedFolders.isNotEmpty(),
                    actionText = if (selectedFolders.isNotEmpty()) "Delete (${selectedFolders.size})" else "Delete",
                    isDestructive = true,
                    onClick = { vm.onDeleteSelected() }
                )
            }
        }
    ) {
        when {
            uiState.isScanning -> CleanerLoadingState("Scanning storage for empty folders...")
            uiState.folders.isEmpty() -> CleanerEmptyState(
                icon = "check_circle",
                title = "No Empty Folders!",
                subtitle = "Your storage is tidy and free of empty directories.",
                onButtonClick = onBack
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(uiState.folders, key = { _, file -> file.absolutePath }) { index, file ->
                            val isSelected = selectedFolders.contains(file.absolutePath)
                            val isStarred = FavoritesManager.isStarred(context, file.absolutePath)
                            val itemShape = getGroupedItemShape(index, uiState.folders.size, outerRadius = 20.dp, innerRadius = 4.dp)

                            Card(
                                onClick = { vm.onToggleSelected(file.absolutePath) },
                                shape = itemShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { vm.onToggleSelected(file.absolutePath) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    MaterialSymbol(
                                        name = "folder_open",
                                        active = true,
                                        size = 24.dp,
                                        tint = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = file.name.ifEmpty { file.absolutePath.substringAfterLast('/') },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isStarred) {
                                                MaterialSymbol("star", active = true, size = 16.dp, tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = file.absolutePath,
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
}
