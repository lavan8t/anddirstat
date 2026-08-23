package com.kd.anddirstat

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FavoritesManager
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

enum class TrashSort(val label: String, val icon: String) {
    NEWEST("Newest First", "schedule"),
    OLDEST("Oldest First", "history"),
    LARGEST("Largest First", "folder_zip"),
    SMALLEST("Smallest First", "sort"),
    NAME_AZ("Name (A-Z)", "sort_by_alpha")
}

class RecycleBinCleanerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AndDirStatAppTheme {
                RecycleBinCleanerView(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isLoading by remember { mutableStateOf(true) }
    var trashedFiles by remember { mutableStateOf<List<TrashedItem>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sortOrder by remember { mutableStateOf(TrashSort.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    var isOperating by remember { mutableStateOf(false) }
    var operationCurrentCount by remember { mutableIntStateOf(0) }
    var operationTotalCount by remember { mutableIntStateOf(0) }
    var operationCurrentFileName by remember { mutableStateOf("") }
    var operationIsTrash by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppNotifier.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

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

                fun scanDir(dir: File) {
                    if (!dir.exists() || !dir.canRead()) return
                    val files = dir.listFiles() ?: return
                    for (f in files) {
                        val name = f.name
                        if (name.startsWith(".trashed") || dir.name.startsWith(".trashed")) {
                            found.add(
                                TrashedItem(
                                    file = f,
                                    cleanName = getCleanFileName(name),
                                    rawName = name,
                                    size = if (f.isDirectory) FileUtils.getFolderSize(f) else f.length(),
                                    lastModified = f.lastModified(),
                                    path = f.absolutePath
                                )
                            )
                        } else if (f.isDirectory && !name.equals("android", ignoreCase = true) && !name.startsWith(".")) {
                            scanDir(f)
                        }
                    }
                }

                val rootStorage = Environment.getExternalStorageDirectory()
                if (rootStorage != null && rootStorage.exists()) {
                    scanDir(rootStorage)
                }

                found.distinctBy { it.path }
            }

            trashedFiles = applySort(results, sortOrder)
            selectedFiles = emptySet()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        scanTrashedItems()
    }

    fun handleDeleteSelected() {
        val toDelete = trashedFiles.filter { selectedFiles.contains(it.path) }
        if (toDelete.isEmpty()) return

        val (starred, unstarred) = toDelete.partition { FavoritesManager.isStarred(context, it.path) }
        if (unstarred.isEmpty()) {
            AppNotifier.notify("Cannot delete starred files. Unstar them first.")
            return
        }

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            isOperating = true
            operationTotalCount = unstarred.size
            operationIsTrash = false
            var deletedCount = 0

            withContext(Dispatchers.IO) {
                unstarred.forEachIndexed { index, item ->
                    operationCurrentCount = index + 1
                    operationCurrentFileName = item.cleanName
                    try {
                        if (item.file.exists()) {
                            if (item.file.deleteRecursively()) {
                                deletedCount++
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            isOperating = false
            val baseMsg = "Permanently deleted $deletedCount items"
            val msg = if (starred.isNotEmpty()) "$baseMsg (Skipped ${starred.size} starred)" else baseMsg
            AppNotifier.notify(msg)
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

    val totalTrashedBytes = remember(trashedFiles) { trashedFiles.sumOf { it.size } }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Recycle Bin",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isLoading && trashedFiles.isNotEmpty()) {
                            Text(
                                text = if (selectedFiles.isNotEmpty())
                                    "${selectedFiles.size} of ${trashedFiles.size} selected"
                                else
                                    "${trashedFiles.size} items (${FileUtils.formatFileSize(totalTrashedBytes, context)}) • ${sortOrder.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MaterialSymbol("arrow_back", active = true, size = 24.dp)
                    }
                },
                actions = {
                    if (!isLoading && trashedFiles.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                MaterialSymbol("sort", active = true, size = 22.dp)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                TrashSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = sort.label,
                                                fontWeight = if (sortOrder == sort) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            MaterialSymbol(
                                                name = sort.icon,
                                                active = sortOrder == sort,
                                                size = 20.dp,
                                                tint = if (sortOrder == sort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {
                                            showSortMenu = false
                                            sortOrder = sort
                                            trashedFiles = applySort(trashedFiles, sort)
                                        }
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFiles = if (selectedFiles.size == trashedFiles.size) {
                                    emptySet()
                                } else {
                                    trashedFiles.map { it.path }.toSet()
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedFiles.size == trashedFiles.size) "Deselect All" else "Select All",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanning Recycle Bin...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trashedFiles.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialSymbol("delete_outline", active = true, size = 40.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Recycle Bin is Empty",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No deleted or trashed files found in storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onBack,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Text("Back to Discover", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(trashedFiles, key = { it.path }) { item ->
                                val isSelected = selectedFiles.contains(item.path)
                                val isStarred = FavoritesManager.isStarred(context, item.path)
                                val compactNode = remember(item) {
                                    CompactNode(
                                        name = item.cleanName,
                                        isDirectory = item.file.isDirectory,
                                        size = item.size
                                    )
                                }

                                ListItem(
                                    headlineContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.cleanName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isStarred) {
                                                MaterialSymbol("star", active = true, size = 16.dp, tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    },
                                    supportingContent = {
                                        Text(
                                            text = "${FileUtils.formatFileSize(item.size, context)}  ${dateFormat.format(Date(item.lastModified))}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedFiles = if (isSelected) selectedFiles - item.path else selectedFiles + item.path
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val isApk = item.cleanName.endsWith(".apk", ignoreCase = true)
                                            val isMedia = item.cleanName.lowercase().let { l ->
                                                l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") ||
                                                l.endsWith(".heic") || l.endsWith(".gif") || l.endsWith(".mp4") || l.endsWith(".mkv") ||
                                                l.endsWith(".avi") || l.endsWith(".mov") || l.endsWith(".webm") || l.endsWith(".3gp") || l.endsWith(".apk")
                                            }

                                            if (isMedia || isApk) {
                                                MediaThumbnailView(
                                                    node = compactNode,
                                                    path = item.path,
                                                    fallbackTint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        MaterialSymbol(
                                                            name = if (item.file.isDirectory) "folder_delete" else "description",
                                                            active = true,
                                                            size = 24.dp,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            // Tap to select overlay badge
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    MaterialSymbol(
                                                        name = "check",
                                                        active = true,
                                                        size = 24.dp,
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                                        else
                                            Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (!item.file.isDirectory) {
                                                FileUtils.openFile(context, item.file)
                                            } else {
                                                AppNotifier.notify("Folder: ${item.cleanName} (${FileUtils.formatFileSize(item.size, context)})")
                                            }
                                        }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 72.dp)
                                )
                            }
                        }

                        // Bottom Action Bar: Side-by-Side Restore & Delete Permanently in SAME Row
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = { handleRestoreSelected() },
                                    enabled = selectedFiles.isNotEmpty(),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MaterialSymbol(
                                            name = "restore_from_trash",
                                            active = selectedFiles.isNotEmpty(),
                                            size = 20.dp,
                                            tint = if (selectedFiles.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = if (selectedFiles.isNotEmpty())
                                                "Restore (${selectedFiles.size})"
                                            else
                                                "Restore",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Button(
                                    onClick = { handleDeleteSelected() },
                                    enabled = selectedFiles.isNotEmpty(),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MaterialSymbol(
                                            name = "delete_forever",
                                            active = selectedFiles.isNotEmpty(),
                                            size = 20.dp,
                                            tint = if (selectedFiles.isNotEmpty()) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = if (selectedFiles.isNotEmpty())
                                                "Delete (${selectedFiles.size})"
                                            else
                                                "Delete",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            DeletionProgressDialog(
                visible = isOperating,
                currentCount = operationCurrentCount,
                totalCount = operationTotalCount,
                currentFileName = operationCurrentFileName,
                isTrash = operationIsTrash
            )
        }
    }
}
