package com.kd.anddirstat.ui.screens.cleaners

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.scanner.TreeCacheManager
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailCache
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileMaintenanceEngine
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

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

private data class SwipeHistoryItem(
    val item: ScreenshotItem,
    val wasDeleted: Boolean,
    val originalIndex: Int
)

@Composable
fun StackScreenshotImage(
    file: File,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(file.absolutePath) {
        mutableStateOf(MediaThumbnailCache.get(file.absolutePath))
    }

    LaunchedEffect(file.absolutePath) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                try {
                    val cached = MediaThumbnailCache.get(file.absolutePath)
                    if (cached != null) cached
                    else {
                        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(file)
                            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                        } else {
                            BitmapFactory.decodeFile(file.absolutePath)
                        }
                        if (bmp != null) MediaThumbnailCache.put(file.absolutePath, bmp)
                        bmp
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            MaterialSymbol(
                name = "screenshot_monitor",
                active = true,
                size = 36.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun ScreenshotsCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(true) }
    var screenshots by remember { mutableStateOf<List<ScreenshotItem>>(emptyList()) }
    var totalInitialCount by remember { mutableIntStateOf(0) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isGridView by remember { mutableStateOf(false) }
    var selectedGridPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sortOrder by remember { mutableStateOf(ScreenshotSort.NEWEST) }

    val historyStack = remember { mutableListOf<SwipeHistoryItem>() }
    var canUndo by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("screenshots_cleaner_prefs", Context.MODE_PRIVATE) }

    val animOffsetX = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerVibration(isDelete: Boolean) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = if (isDelete) 220 else 90
                    val duration = if (isDelete) 24L else 14L
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(if (isDelete) 24L else 14L)
                }
            } else {
                haptic.performHapticFeedback(if (isDelete) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Exception) {}
    }

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
                val keptSet = prefs.getStringSet("kept_screenshots", emptySet()) ?: emptySet()
                val items = mutableListOf<ScreenshotItem>()
                val root = TreeCacheManager.loadTree(context)
                if (root != null) {
                    val list = StorageFilterHelper.getScreenshots(root, limit = 500)
                    for (entry in list) {
                        val f = FileUtils.resolveActualFile(entry.path) ?: FileUtils.resolveActualFile(entry.node.name)
                        if (f != null && f.exists() && f.canRead() && f.absolutePath !in keptSet) {
                            items.add(ScreenshotItem(file = f, name = entry.node.name, size = entry.node.size, path = entry.path))
                        }
                    }
                }

                if (items.isEmpty()) {
                    val rootDir = Environment.getExternalStorageDirectory()
                    val targetDirs = listOf(
                        File(rootDir, "Pictures/Screenshots"),
                        File(rootDir, "DCIM/Screenshots"),
                        File(rootDir, "Pictures/Screen recordings"),
                        File(rootDir, "DCIM/Screen recordings"),
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots")
                    )

                    for (dir in targetDirs) {
                        if (dir.exists() && dir.isDirectory) {
                            dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEach { f ->
                                val name = f.name.lowercase()
                                if ((name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".heic")) && f.absolutePath !in keptSet) {
                                    items.add(ScreenshotItem(file = f, name = f.name, size = f.length(), path = f.absolutePath))
                                }
                            }
                        }
                    }
                }
                sortScreenshotList(items.distinctBy { it.file.absolutePath }, sortOrder)
            }

            screenshots = results
            totalInitialCount = results.size
            currentIndex = 0
            selectedGridPaths = emptySet()
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        scanScreenshots()
    }

    // Preload next screenshots for smooth swiping
    LaunchedEffect(currentIndex, screenshots) {
        if (screenshots.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                for (i in currentIndex until minOf(screenshots.size, currentIndex + 5)) {
                    val file = screenshots[i].file
                    if (MediaThumbnailCache.get(file.absolutePath) == null) {
                        try {
                            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val source = ImageDecoder.createSource(file)
                                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                }
                            } else {
                                BitmapFactory.decodeFile(file.absolutePath)
                            }
                            if (bmp != null) MediaThumbnailCache.put(file.absolutePath, bmp)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    val currentItem = screenshots.getOrNull(currentIndex)

    fun handleKeep() {
        val item = currentItem ?: return
        triggerVibration(isDelete = false)
        val currentKept = prefs.getStringSet("kept_screenshots", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentKept.add(item.file.absolutePath)
        prefs.edit().putStringSet("kept_screenshots", currentKept).apply()

        historyStack.add(SwipeHistoryItem(item = item, wasDeleted = false, originalIndex = currentIndex))
        canUndo = true

        val nextList = screenshots.toMutableList()
        nextList.removeAt(currentIndex)
        screenshots = nextList
        if (currentIndex >= screenshots.size) {
            currentIndex = maxOf(0, screenshots.size - 1)
        }
    }

    fun handleDelete() {
        val item = currentItem ?: return
        if (FavoritesManager.isStarred(context, item.path)) {
            triggerVibration(isDelete = true)
            AppNotifier.notify("Starred screenshot is protected.")
            return
        }

        triggerVibration(isDelete = true)
        val deletedFile = item.file
        val deletedIndex = currentIndex

        historyStack.add(SwipeHistoryItem(item = item, wasDeleted = true, originalIndex = deletedIndex))
        canUndo = true

        val nextList = screenshots.toMutableList()
        nextList.removeAt(deletedIndex)
        screenshots = nextList
        if (currentIndex >= screenshots.size) {
            currentIndex = maxOf(0, screenshots.size - 1)
        }

        scope.launch(Dispatchers.IO) {
            try {
                FileUtils.deleteOrTrashFile(deletedFile, context)
            } catch (_: Exception) {}
        }
    }

    fun handleUndo() {
        if (historyStack.isEmpty()) return
        val last = historyStack.removeLast()
        canUndo = historyStack.isNotEmpty()

        if (last.wasDeleted) {
            scope.launch(Dispatchers.IO) {
                try {
                    FileUtils.restoreTrashedFile(last.item.file, context)
                } catch (_: Exception) {}
            }
        } else {
            val currentKept = prefs.getStringSet("kept_screenshots", emptySet())?.toMutableSet() ?: mutableSetOf()
            currentKept.remove(last.item.file.absolutePath)
            prefs.edit().putStringSet("kept_screenshots", currentKept).apply()
        }

        val restoredList = screenshots.toMutableList()
        val insertIndex = minOf(last.originalIndex, restoredList.size)
        restoredList.add(insertIndex, last.item)
        screenshots = restoredList
        currentIndex = insertIndex
        AppNotifier.notify("Undid review for ${last.item.name}")
    }

    fun handleBatchDeleteGrid() {
        val toDelete = screenshots.filter { selectedGridPaths.contains(it.path) }.map { it.file }
        if (toDelete.isEmpty()) return

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            FileMaintenanceEngine.batchDelete(
                context = context,
                files = toDelete,
                isPermanent = false,
                actionName = "Moved to Recycle Bin"
            )
            scanScreenshots()
        }
    }

    val totalSelectedGridSize = remember(screenshots, selectedGridPaths) {
        screenshots.filter { selectedGridPaths.contains(it.path) }.sumOf { it.size }
    }

    CleanerScaffold(
        title = "Screenshots",
        selectedCount = if (isGridView) selectedGridPaths.size else 0,
        totalCount = screenshots.size,
        isLoading = isScanning,
        onBack = onBack,
        onSelectAllToggle = if (isGridView && screenshots.isNotEmpty()) {
            {
                selectedGridPaths = if (selectedGridPaths.size == screenshots.size) emptySet() else screenshots.map { it.path }.toSet()
            }
        } else null,
        actions = {
            if (!isScanning && screenshots.isNotEmpty()) {
                if (canUndo && !isGridView) {
                    IconButton(onClick = { handleUndo() }) {
                        MaterialSymbol("undo", active = true, size = 22.dp)
                    }
                }
                CleanerSortDropdown(
                    options = ScreenshotSort.entries.toTypedArray(),
                    selected = sortOrder,
                    onSelect = {
                        sortOrder = it
                        screenshots = sortScreenshotList(screenshots, it)
                        currentIndex = 0
                    }
                )
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isGridView = !isGridView
                }) {
                    MaterialSymbol(name = if (isGridView) "swipe" else "grid_view", active = true)
                }
            }
        },
        bottomBar = {
            if (isGridView && !isScanning && screenshots.isNotEmpty()) {
                CleanerBottomActionBar(
                    visible = true,
                    enabled = selectedGridPaths.isNotEmpty(),
                    actionText = if (selectedGridPaths.isNotEmpty()) "Clean ${FileUtils.formatFileSize(totalSelectedGridSize)} (${selectedGridPaths.size})" else "Clean",
                    actionIcon = "delete",
                    isDestructive = true,
                    onClick = { handleBatchDeleteGrid() }
                )
            }
        }
    ) {
        when {
            isScanning -> CleanerLoadingState("Scanning for screenshots...")
            screenshots.isEmpty() -> CleanerEmptyState(
                icon = "check_circle",
                title = "All Reviewed!",
                subtitle = "No more screenshots to clean. You're all caught up!",
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
                        val isSelected = selectedGridPaths.contains(item.path)
                        val isStarred = FavoritesManager.isStarred(context, item.path)

                        Card(
                            onClick = {
                                selectedGridPaths = if (isSelected) selectedGridPaths - item.path else selectedGridPaths + item.path
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
                                            selectedGridPaths = if (checked) selectedGridPaths + item.path else selectedGridPaths - item.path
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
                // Swipe Deck Hero Mode
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentItem != null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Subtitle counter
                            Text(
                                text = "${currentIndex + 1} of ${screenshots.size} remaining",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(Modifier.height(8.dp))

                            // Interactive Card Deck
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background Stack Cards (up to 2 layers behind)
                                for (stackIdx in minOf(currentIndex + 2, screenshots.size - 1) downTo currentIndex + 1) {
                                    val depth = stackIdx - currentIndex
                                    val scale = 1f - (depth * 0.05f)
                                    val offsetY = (depth * 12).dp

                                    Card(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                                        modifier = Modifier
                                            .fillMaxHeight(0.92f)
                                            .fillMaxWidth(0.88f)
                                            .offset(y = offsetY)
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                                alpha = 1f - (depth * 0.25f)
                                            }
                                            .clip(RoundedCornerShape(24.dp))
                                    ) {
                                        StackScreenshotImage(
                                            file = screenshots[stackIdx].file,
                                            contentDescription = screenshots[stackIdx].name
                                        )
                                    }
                                }

                                // Top Active Draggable Card
                                Card(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    modifier = Modifier
                                        .fillMaxHeight(0.92f)
                                        .fillMaxWidth(0.88f)
                                        .offset { IntOffset(animOffsetX.value.roundToInt(), 0) }
                                        .graphicsLayer {
                                            rotationZ = (animOffsetX.value / 35f).coerceIn(-15f, 15f)
                                        }
                                        .clip(RoundedCornerShape(24.dp))
                                        .pointerInput(currentItem.path) {
                                            detectHorizontalDragGestures(
                                                onDragStart = { isDragging = true },
                                                onDragEnd = {
                                                    isDragging = false
                                                    scope.launch {
                                                        val offsetX = animOffsetX.value
                                                        if (offsetX > 120f) {
                                                            animOffsetX.animateTo(1200f, spring(stiffness = Spring.StiffnessMedium))
                                                            handleKeep()
                                                            animOffsetX.snapTo(0f)
                                                        } else if (offsetX < -120f) {
                                                            animOffsetX.animateTo(-1200f, spring(stiffness = Spring.StiffnessMedium))
                                                            handleDelete()
                                                            animOffsetX.snapTo(0f)
                                                        } else {
                                                            animOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    isDragging = false
                                                    scope.launch {
                                                        animOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                                    }
                                                },
                                                onHorizontalDrag = { _, dragAmount ->
                                                    scope.launch {
                                                        animOffsetX.snapTo(animOffsetX.value + dragAmount)
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        StackScreenshotImage(
                                            file = currentItem.file,
                                            contentDescription = currentItem.name
                                        )

                                        // Left Drag Delete Overlay
                                        val leftAlpha = (-animOffsetX.value / 160f).coerceIn(0f, 0.85f)
                                        if (leftAlpha > 0.05f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.error.copy(alpha = leftAlpha)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    MaterialSymbol("delete_forever", active = true, size = 40.dp, tint = MaterialTheme.colorScheme.onError)
                                                    Text(
                                                        text = "DELETE",
                                                        style = MaterialTheme.typography.headlineMedium,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.onError
                                                    )
                                                }
                                            }
                                        }

                                        // Right Drag Keep Overlay
                                        val rightAlpha = (animOffsetX.value / 160f).coerceIn(0f, 0.85f)
                                        if (rightAlpha > 0.05f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = rightAlpha)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    MaterialSymbol("check", active = true, size = 40.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                                    Text(
                                                        text = "KEEP",
                                                        style = MaterialTheme.typography.headlineMedium,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // File details
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = currentItem.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = FileUtils.formatFileSize(currentItem.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // Bottom Keep & Delete Action Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            animOffsetX.animateTo(-1200f, spring(stiffness = Spring.StiffnessMedium))
                                            handleDelete()
                                            animOffsetX.snapTo(0f)
                                        }
                                    },
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
                                        MaterialSymbol("delete", active = true, size = 20.dp, tint = MaterialTheme.colorScheme.onError)
                                        Text("Delete", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }

                                FilledTonalButton(
                                    onClick = {
                                        scope.launch {
                                            animOffsetX.animateTo(1200f, spring(stiffness = Spring.StiffnessMedium))
                                            handleKeep()
                                            animOffsetX.snapTo(0f)
                                        }
                                    },
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MaterialSymbol("check", active = true, size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)
                                        Text("Keep", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
