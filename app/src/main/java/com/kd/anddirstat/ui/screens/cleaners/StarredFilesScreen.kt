package com.kd.anddirstat.ui.screens.cleaners

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.viewmodel.AndroidCleanerHaptics
import com.kd.anddirstat.viewmodel.AndroidCleanerNotifier
import com.kd.anddirstat.viewmodel.AndroidFileOpener
import com.kd.anddirstat.viewmodel.AndroidStarredSource
import com.kd.anddirstat.viewmodel.AndroidStarRepository
import com.kd.anddirstat.viewmodel.CleanerViewModelFactory
import com.kd.anddirstat.viewmodel.StarredFilesViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StarredFilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val appContext = context.applicationContext

    val vm: StarredFilesViewModel = viewModel(
        factory = CleanerViewModelFactory {
            StarredFilesViewModel(
                source = AndroidStarredSource(appContext),
                stars = AndroidStarRepository(appContext),
                haptics = AndroidCleanerHaptics(appContext, haptic),
                notifier = AndroidCleanerNotifier(),
                opener = AndroidFileOpener(appContext)
            )
        }
    )
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.onRefresh() }

    val selectedPaths = uiState.selectedPaths
    val filteredList = uiState.filteredItems

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { vm.onSearchQueryChanged(it) },
                            placeholder = { Text("Search starred files…") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = if (selectedPaths.isNotEmpty()) "${selectedPaths.size} Selected" else "Starred Files",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isSearchActive) {
                            vm.onSearchActiveChanged(false)
                        } else {
                            onBack()
                        }
                    }) {
                        MaterialSymbol(name = "arrow_back", active = true)
                    }
                },
                actions = {
                    if (!uiState.isSearchActive) {
                        IconButton(onClick = { vm.onSearchActiveChanged(true) }) {
                            MaterialSymbol(name = "search", active = true)
                        }
                        IconButton(onClick = { vm.onToggleGrid() }) {
                            MaterialSymbol(name = if (uiState.isGridView) "view_list" else "grid_view", active = true)
                        }
                    } else if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.onSearchQueryChanged("") }) {
                            MaterialSymbol(name = "close", active = true)
                        }
                    }
                    if (selectedPaths.isNotEmpty()) {
                        IconButton(onClick = { vm.onUnstarSelected() }) {
                            MaterialSymbol(name = "star_outline", active = true, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedPaths.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedPaths.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { vm.onUnstarSelected() }) {
                                MaterialSymbol(name = "star_outline", active = true, size = 18.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Unstar")
                            }
                            IconButton(onClick = { vm.onClearSelection() }) {
                                MaterialSymbol(name = "close", active = true)
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        if (uiState.items.isEmpty()) {
            CleanerEmptyState(
                icon = "star",
                title = "No Starred Files",
                subtitle = "Star files in Explorer or Treemap to protect them and access them quickly.",
                onButtonClick = onBack
            )
        } else if (uiState.isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    start = 8.dp,
                    end = 8.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList, key = { it.path }) { item ->
                    val isSelected = selectedPaths.contains(item.path)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    if (selectedPaths.isNotEmpty()) {
                                        vm.onToggleSelected(item.path)
                                    } else if (item.exists) {
                                        vm.onOpenItem(item.path)
                                    }
                                },
                                onLongClick = { vm.onToggleSelected(item.path) }
                            )
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
                                if (item.isDirectory) {
                                    MaterialSymbol("folder", active = true, size = 28.dp, tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    MediaThumbnailView(
                                        path = item.path,
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                }
                                IconButton(
                                    onClick = { vm.onToggleStar(item.path) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    MaterialSymbol("star", active = true, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.isDirectory) "Directory" else FileUtils.formatFileSize(item.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    start = 12.dp,
                    end = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList, key = { it.path }) { item ->
                    val isSelected = selectedPaths.contains(item.path)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectedPaths.isNotEmpty()) {
                                        vm.onToggleSelected(item.path)
                                    } else if (item.exists) {
                                        vm.onOpenItem(item.path)
                                    }
                                },
                                onLongClick = { vm.onToggleSelected(item.path) }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.isDirectory) {
                                MaterialSymbol("folder", active = true, size = 28.dp, tint = MaterialTheme.colorScheme.primary)
                            } else {
                                MediaThumbnailView(
                                    path = item.path,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.isDirectory) item.path else "${FileUtils.formatFileSize(item.size)} • ${item.path}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { vm.onToggleStar(item.path) }) {
                                MaterialSymbol("star", active = true, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
