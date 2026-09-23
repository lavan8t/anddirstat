package com.kd.anddirstat.ui.screens.cleaners

import android.os.Environment
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileMaintenanceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun EmptyFoldersCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(true) }
    var emptyFolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isDeleting by remember { mutableStateOf(false) }
    var deleteCurrentCount by remember { mutableIntStateOf(0) }
    var deleteTotalCount by remember { mutableIntStateOf(0) }
    var deleteCurrentFileName by remember { mutableStateOf("") }

    fun scanEmptyFolders() {
        scope.launch {
            isScanning = true
            val results = withContext(Dispatchers.IO) {
                val found = mutableListOf<File>()

                fun isFolderEmpty(dir: File): Boolean {
                    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return false
                    val name = dir.name.lowercase()
                    if (name == "android" || name.startsWith(".trashed") || name == "system volume information") return false
                    val children = dir.listFiles() ?: return false
                    if (children.isEmpty()) {
                        found.add(dir)
                        return true
                    }
                    var allSubEmpty = true
                    for (child in children) {
                        if (child.isDirectory) {
                            if (!isFolderEmpty(child)) allSubEmpty = false
                        } else {
                            allSubEmpty = false
                        }
                    }
                    if (allSubEmpty) {
                        found.add(dir)
                        return true
                    }
                    return false
                }

                val rootStorage = Environment.getExternalStorageDirectory()
                if (rootStorage != null && rootStorage.exists()) {
                    val topDirs = rootStorage.listFiles()?.filter { it.isDirectory && it.canRead() } ?: emptyList()
                    for (dir in topDirs) {
                        val n = dir.name.lowercase()
                        if (n != "android" && !n.startsWith(".")) {
                            isFolderEmpty(dir)
                        }
                    }
                }

                found.distinctBy { it.absolutePath }.sortedBy { it.absolutePath }
            }

            emptyFolders = results
            selectedFolders = results.map { it.absolutePath }.toSet()
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        scanEmptyFolders()
    }

    fun handleDeleteSelected() {
        val toDelete = emptyFolders.filter { selectedFolders.contains(it.absolutePath) }
            .sortedByDescending { it.absolutePath.length }
        if (toDelete.isEmpty()) return

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            isDeleting = true
            FileMaintenanceEngine.batchDelete(
                context = context,
                files = toDelete,
                isPermanent = true,
                actionName = "Deleted"
            ) { curr, tot, name ->
                deleteCurrentCount = curr
                deleteTotalCount = tot
                deleteCurrentFileName = name
            }
            isDeleting = false
            scanEmptyFolders()
        }
    }

    CleanerScaffold(
        title = "Empty Folders",
        selectedCount = selectedFolders.size,
        totalCount = emptyFolders.size,
        isLoading = isScanning,
        isOperating = isDeleting,
        operationProgress = Triple(deleteCurrentCount, deleteTotalCount, deleteCurrentFileName),
        onBack = onBack,
        onSelectAllToggle = {
            selectedFolders = if (selectedFolders.size == emptyFolders.size) emptySet() else emptyFolders.map { it.absolutePath }.toSet()
        },
        bottomBar = {
            if (!isScanning && emptyFolders.isNotEmpty()) {
                CleanerBottomActionBar(
                    visible = true,
                    enabled = selectedFolders.isNotEmpty(),
                    actionText = if (selectedFolders.isNotEmpty()) "Delete (${selectedFolders.size})" else "Delete",
                    isDestructive = true,
                    onClick = { handleDeleteSelected() }
                )
            }
        }
    ) {
        when {
            isScanning -> CleanerLoadingState("Scanning storage for empty folders...")
            emptyFolders.isEmpty() -> CleanerEmptyState(
                icon = "check_circle",
                title = "No Empty Folders!",
                subtitle = "Your storage is tidy and free of empty directories.",
                onButtonClick = onBack
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(340.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(emptyFolders, key = { it.absolutePath }) { file ->
                            val isSelected = selectedFolders.contains(file.absolutePath)
                            val isStarred = FavoritesManager.isStarred(context, file.absolutePath)

                            Card(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFolders = if (isSelected) selectedFolders - file.absolutePath else selectedFolders + file.absolutePath
                                },
                                shape = RoundedCornerShape(16.dp),
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
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedFolders = if (checked) selectedFolders + file.absolutePath else selectedFolders - file.absolutePath
                                        },
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
