package com.kd.anddirstat

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.scanner.TreeCacheManager
import com.kd.anddirstat.ui.components.AppTooltip
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

data class DuplicateFileItem(
    val file: File,
    val node: CompactNode,
    val path: String,
    val lastModified: Long
)

data class DuplicateGroup(
    val key: String,
    val fileSize: Long,
    val files: List<DuplicateFileItem>
)

enum class DuplicateSort(val label: String, val icon: String) {
    LARGEST("Largest Size First", "folder_zip"),
    MOST_COPIES("Most Copies First", "content_copy"),
    NAME_AZ("Name (A to Z)", "sort_by_alpha")
}

class DuplicatesCleanerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AndDirStatAppTheme {
                DuplicatesCleanerView(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var showSortMenu by remember { mutableStateOf(false) }
    var showAutoSelectMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var isOperating by remember { mutableStateOf(false) }
    var operationCurrentCount by remember { mutableIntStateOf(0) }
    var operationTotalCount by remember { mutableIntStateOf(0) }
    var operationCurrentFileName by remember { mutableStateOf("") }
    var operationIsTrash by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        AppNotifier.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun scanDuplicates() {
        scope.launch {
            isScanning = true
            val groups = withContext(Dispatchers.IO) {
                val candidateEntries = mutableListOf<Pair<CompactNode, String>>()
                val root = TreeCacheManager.loadTree(context)
                if (root != null) {
                    val stack = ArrayDeque<Pair<CompactNode, String>>()
                    stack.add(root to "")
                    while (stack.isNotEmpty()) {
                        val (node, currentPath) = stack.removeLast()
                        val path = if (currentPath.isEmpty()) node.name else "$currentPath/${node.name}"
                        if (!node.isDirectory && node.size > 1024L && !node.name.startsWith(".trashed") && !path.contains("[Recycle Bin]")) {
                            candidateEntries.add(node to path)
                        } else {
                            node.children?.forEach { stack.add(it to path) }
                        }
                    }
                }

                if (candidateEntries.isEmpty()) {
                    val rootStorage = Environment.getExternalStorageDirectory()
                    if (rootStorage != null && rootStorage.exists()) {
                        val stack = ArrayDeque<File>()
                        stack.add(rootStorage)
                        while (stack.isNotEmpty() && candidateEntries.size < 40000) {
                            val dir = stack.removeLast()
                            val files = dir.listFiles() ?: continue
                            for (f in files) {
                                if (f.isFile && f.length() > 1024L && !f.name.startsWith(".")) {
                                    val node = CompactNode(name = f.name, isDirectory = false, size = f.length())
                                    candidateEntries.add(node to f.absolutePath)
                                } else if (f.isDirectory && !f.name.equals("Android", ignoreCase = true) && !f.name.startsWith(".")) {
                                    stack.add(f)
                                }
                            }
                        }
                    }
                }

                // Fast candidate grouping by file size
                val bySize = candidateEntries.groupBy { it.first.size }
                val duplicateCandidates = bySize.filter { it.value.size > 1 }

                val resultGroups = mutableListOf<DuplicateGroup>()
                for ((size, list) in duplicateCandidates) {
                    val resolvedItems = mutableListOf<DuplicateFileItem>()
                    for ((node, path) in list) {
                        val f = FileUtils.resolveActualFile(path) ?: FileUtils.resolveActualFile(node.name) ?: File(path)
                        if (f.exists() && f.canRead()) {
                            resolvedItems.add(DuplicateFileItem(file = f, node = node, path = path, lastModified = f.lastModified()))
                        }
                    }
                    val distinct = resolvedItems.distinctBy { it.file.absolutePath }
                    if (distinct.size > 1) {
                        resultGroups.add(
                            DuplicateGroup(
                                key = "${distinct.first().node.name}_$size",
                                fileSize = size,
                                files = distinct.sortedBy { it.lastModified }
                            )
                        )
                    }
                }
                resultGroups
            }

            duplicateGroups = groups
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        scanDuplicates()
    }

    val sortedGroups = remember(duplicateGroups, sortOrder) {
        when (sortOrder) {
            DuplicateSort.LARGEST -> duplicateGroups.sortedByDescending { it.fileSize * (it.files.size - 1) }
            DuplicateSort.MOST_COPIES -> duplicateGroups.sortedByDescending { it.files.size }
            DuplicateSort.NAME_AZ -> duplicateGroups.sortedBy { it.files.firstOrNull()?.file?.name?.lowercase() ?: "" }
        }
    }

    val totalDuplicateCount = remember(duplicateGroups) {
        duplicateGroups.sumOf { it.files.size }
    }
    val totalWastedBytes = remember(duplicateGroups) {
        duplicateGroups.sumOf { it.fileSize * (it.files.size - 1) }
    }

    val selectedFiles = remember(selectedPaths, duplicateGroups) {
        val all = duplicateGroups.flatMap { it.files }
        all.filter { selectedPaths.contains(it.file.absolutePath) }
    }
    val selectedTotalBytes = remember(selectedFiles) {
        selectedFiles.sumOf { it.file.length() }
    }

    BackHandler(enabled = selectedPaths.isNotEmpty()) {
        selectedPaths = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MaterialSymbol(
                            name = "arrow_back",
                            active = true,
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Text(
                        text = "Duplicate Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (!isScanning && duplicateGroups.isNotEmpty()) {
                        // Auto-Select Menu
                        Box {
                            AppTooltip(text = "Auto-select duplicates") {
                                IconButton(onClick = { showAutoSelectMenu = true }) {
                                    MaterialSymbol(
                                        name = "auto_fix_high",
                                        active = true,
                                        size = 22.dp,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showAutoSelectMenu,
                                onDismissRequest = { showAutoSelectMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Select all older copies (Keep newest)") },
                                    leadingIcon = { MaterialSymbol("history", active = true, size = 20.dp) },
                                    onClick = {
                                        showAutoSelectMenu = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val toSelect = mutableSetOf<String>()
                                        for (group in duplicateGroups) {
                                            val sorted = group.files.sortedBy { it.lastModified }
                                            // Keep newest (last item), select all older
                                            toSelect.addAll(sorted.dropLast(1).map { it.file.absolutePath })
                                        }
                                        selectedPaths = toSelect
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select all newer copies (Keep oldest)") },
                                    leadingIcon = { MaterialSymbol("schedule", active = true, size = 20.dp) },
                                    onClick = {
                                        showAutoSelectMenu = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val toSelect = mutableSetOf<String>()
                                        for (group in duplicateGroups) {
                                            val sorted = group.files.sortedBy { it.lastModified }
                                            // Keep oldest (first item), select all newer
                                            toSelect.addAll(sorted.drop(1).map { it.file.absolutePath })
                                        }
                                        selectedPaths = toSelect
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Deselect all") },
                                    leadingIcon = { MaterialSymbol("clear_all", active = true, size = 20.dp) },
                                    onClick = {
                                        showAutoSelectMenu = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedPaths = emptySet()
                                    }
                                )
                            }
                        }

                        // Sort Menu
                        Box {
                            AppTooltip(text = "Sort duplicates") {
                                IconButton(onClick = { showSortMenu = true }) {
                                    MaterialSymbol(
                                        name = sortOrder.icon,
                                        active = true,
                                        size = 22.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DuplicateSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = sort.label,
                                                fontWeight = if (sort == sortOrder) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            MaterialSymbol(
                                                name = sort.icon,
                                                active = sort == sortOrder,
                                                size = 20.dp,
                                                tint = if (sort == sortOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            sortOrder = sort
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isScanning -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Finding duplicate files...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                duplicateGroups.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialSymbol(
                                    name = "check_circle",
                                    active = true,
                                    size = 44.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "No duplicate files found",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your storage is clean and free of redundant duplicate copies.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = sortedGroups,
                            key = { group -> group.key }
                        ) { group ->
                            val isExpanded = expandedGroupKeys.contains(group.key)
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Group Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    expandedGroupKeys = if (isExpanded) expandedGroupKeys - group.key else expandedGroupKeys + group.key
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            MaterialSymbol(
                                                name = if (isExpanded) "expand_more" else "chevron_right",
                                                active = true,
                                                size = 22.dp,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = group.files.firstOrNull()?.node?.name ?: "Duplicates",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${group.files.size} copies  (${FileUtils.formatFileSize(group.fileSize * (group.files.size - 1))} wasted)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        // Quick group selector
                                        val allGroupPaths = group.files.map { it.file.absolutePath }.toSet()
                                        val isGroupFullySelected = allGroupPaths.all { selectedPaths.contains(it) }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    val olderCopies = group.files.sortedBy { it.lastModified }.dropLast(1).map { it.file.absolutePath }
                                                    selectedPaths = if (olderCopies.all { selectedPaths.contains(it) }) {
                                                        selectedPaths - olderCopies.toSet()
                                                    } else {
                                                        selectedPaths + olderCopies.toSet()
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("Select Older", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }

                                            Checkbox(
                                                checked = isGroupFullySelected,
                                                onCheckedChange = { checked ->
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedPaths = if (checked) {
                                                        selectedPaths + allGroupPaths
                                                    } else {
                                                        selectedPaths - allGroupPaths
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Group Items List using Compose ListItem
                                        group.files.forEachIndexed { index, item ->
                                        val isSelected = selectedPaths.contains(item.file.absolutePath)
                                        val isStarred = FavoritesManager.isStarred(context, item.path)
                                        val isMedia = remember(item.node.name) {
                                            val l = item.node.name.lowercase()
                                            l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") ||
                                            l.endsWith(".heic") || l.endsWith(".gif") || l.endsWith(".mp4") || l.endsWith(".mkv") ||
                                            l.endsWith(".apk")
                                        }
                                        val childColor = remember(item.node) { FileUtils.getNodeIconColor(item.node, false) }

                                        ListItem(
                                            leadingContent = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = isSelected,
                                                        onCheckedChange = { checked ->
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            selectedPaths = if (checked) selectedPaths + item.file.absolutePath else selectedPaths - item.file.absolutePath
                                                        },
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = MaterialTheme.colorScheme.primary
                                                        )
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                FileUtils.openFile(context, item.file)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isMedia) {
                                                            MediaThumbnailView(
                                                                node = item.node,
                                                                path = item.path,
                                                                fallbackTint = childColor,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        } else {
                                                            MaterialSymbol(
                                                                name = FileUtils.getNodeSymbolName(item.node, false),
                                                                active = true,
                                                                size = 24.dp,
                                                                tint = childColor
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            headlineContent = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = if (index == 0) "Copy #${index + 1} (Oldest)" else if (index == group.files.lastIndex) "Copy #${index + 1} (Newest)" else "Copy #${index + 1}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (isStarred) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        MaterialSymbol("star", active = true, size = 16.dp, tint = Color(0xFFFFB300))
                                                    }
                                                }
                                            },
                                            supportingContent = {
                                                Column(modifier = Modifier.padding(top = 2.dp)) {
                                                    Text(
                                                        text = item.file.parent?.replace(Environment.getExternalStorageDirectory().absolutePath, "Storage") ?: item.path,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = dateFormat.format(Date(item.lastModified)),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                }
                                            },
                                            colors = ListItemDefaults.colors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedPaths = if (isSelected) selectedPaths - item.file.absolutePath else selectedPaths + item.file.absolutePath
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Selection Bar
            AnimatedVisibility(
                visible = selectedPaths.isNotEmpty(),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh)
                ) + fadeIn(animationSpec = tween(120, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.92f),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessHigh)
                ) + fadeOut(animationSpec = tween(100, easing = FastOutSlowInEasing)) + scaleOut(targetScale = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 4.dp,
                    tonalElevation = 3.dp,
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
                                            selectedPaths = emptySet()
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
                                    text = "${selectedPaths.size} selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = FileUtils.formatFileSize(selectedTotalBytes),
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
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog && selectedPaths.isNotEmpty()) {
            val count = selectedPaths.size
            val starredCount = selectedFiles.count { FavoritesManager.isStarred(context, it.path) }

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
                title = { Text(text = "Move $count duplicates to Recycle Bin?") },
                text = {
                    Column {
                        Text(text = "Total space to free: ${FileUtils.formatFileSize(selectedTotalBytes)}")
                        if (starredCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⚠️ $starredCount starred file(s) are protected and will be skipped.",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            val toDelete = selectedFiles.filter { !FavoritesManager.isStarred(context, it.path) }
                            if (toDelete.isEmpty()) {
                                AppNotifier.notify("All selected files are starred and protected.")
                                return@TextButton
                            }

                            val deletedFilesList = toDelete.map { it.file }
                            scope.launch {
                                isOperating = true
                                operationTotalCount = toDelete.size
                                operationCurrentCount = 0
                                operationIsTrash = true

                                withContext(Dispatchers.IO) {
                                    for ((idx, item) in toDelete.withIndex()) {
                                        operationCurrentCount = idx + 1
                                        operationCurrentFileName = item.file.name
                                        try {
                                            FileUtils.deleteOrTrashFile(item.file, context)
                                        } catch (_: Exception) {}
                                    }
                                }

                                isOperating = false
                                val deletedCount = toDelete.size
                                selectedPaths = emptySet()
                                scanDuplicates()

                                val snackResult = snackbarHostState.showSnackbar(
                                    message = "Moved $deletedCount duplicate file(s) to Recycle Bin",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (snackResult == SnackbarResult.ActionPerformed) {
                                    withContext(Dispatchers.IO) {
                                        for (f in deletedFilesList) {
                                            try {
                                                FileUtils.restoreTrashedFile(f, context)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    scanDuplicates()
                                    AppNotifier.notify("Restored $deletedCount duplicate file(s)")
                                }
                            }
                        }
                    ) {
                        Text("Move to Bin", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
            visible = isOperating,
            currentCount = operationCurrentCount,
            totalCount = operationTotalCount,
            currentFileName = operationCurrentFileName,
            isTrash = operationIsTrash
        )
    }
}
