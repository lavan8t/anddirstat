package com.kd.anddirstat.ui.screens.cleaners

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailCache
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.viewmodel.AndroidCleanerDeleter
import com.kd.anddirstat.viewmodel.AndroidCleanerHaptics
import com.kd.anddirstat.viewmodel.AndroidCleanerNotifier
import com.kd.anddirstat.viewmodel.AndroidKeptScreenshotsStore
import com.kd.anddirstat.viewmodel.AndroidScreenshotSource
import com.kd.anddirstat.viewmodel.AndroidStarRepository
import com.kd.anddirstat.viewmodel.AndroidTrashRestorer
import com.kd.anddirstat.viewmodel.CleanerViewModelFactory
import com.kd.anddirstat.viewmodel.ScreenshotSort
import com.kd.anddirstat.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

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
    val appContext = context.applicationContext

    val vm: ScreenshotsViewModel = viewModel(
        factory = CleanerViewModelFactory {
            ScreenshotsViewModel(
                source = AndroidScreenshotSource(appContext),
                keptStore = AndroidKeptScreenshotsStore(appContext),
                stars = AndroidStarRepository(appContext),
                haptics = AndroidCleanerHaptics(appContext, haptic),
                notifier = AndroidCleanerNotifier(),
                deleter = AndroidCleanerDeleter(appContext),
                restorer = AndroidTrashRestorer(appContext)
            )
        }
    )
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.onScan() }

    // Gesture/animation state stays in the View; only screen state lives in the ViewModel.
    val animOffsetX = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.currentIndex, uiState.items) {
        if (uiState.items.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                for (i in uiState.currentIndex until minOf(uiState.items.size, uiState.currentIndex + 5)) {
                    val file = uiState.items[i].file
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

    val currentItem = uiState.currentItem
    val selectedPaths = uiState.selection.selectedPaths
    val totalSelectedGridSize = remember(uiState.items, selectedPaths) {
        uiState.items.filter { it.path in selectedPaths }.sumOf { it.size }
    }

    CleanerScaffold(
        title = "Screenshots",
        selectedCount = if (uiState.isGridView) selectedPaths.size else 0,
        totalCount = uiState.items.size,
        isLoading = uiState.isScanning,
        onBack = onBack,
        onSelectAllToggle = if (uiState.isGridView && uiState.items.isNotEmpty()) {
            { vm.onSelectAllToggle() }
        } else null,
        actions = {
            if (!uiState.isScanning && uiState.items.isNotEmpty()) {
                if (uiState.canUndo && !uiState.isGridView) {
                    IconButton(onClick = { vm.onUndo() }) {
                        MaterialSymbol("undo", active = true, size = 22.dp)
                    }
                }
                CleanerSortDropdown(
                    options = ScreenshotSort.entries.toTypedArray(),
                    selected = uiState.selection.sortOrder,
                    onSelect = { vm.onSortChanged(it) }
                )
                IconButton(onClick = { vm.onToggleGrid() }) {
                    MaterialSymbol(name = if (uiState.isGridView) "swipe" else "grid_view", active = true)
                }
            }
        },
        bottomBar = {
            if (uiState.isGridView && !uiState.isScanning && uiState.items.isNotEmpty()) {
                CleanerBottomActionBar(
                    visible = true,
                    enabled = selectedPaths.isNotEmpty(),
                    actionText = if (selectedPaths.isNotEmpty()) "Clean ${FileUtils.formatFileSize(totalSelectedGridSize)} (${selectedPaths.size})" else "Clean",
                    actionIcon = "delete",
                    isDestructive = true,
                    onClick = { vm.onBatchDelete() }
                )
            }
        }
    ) {
        when {
            uiState.isScanning -> CleanerLoadingState("Scanning for screenshots...")
            uiState.items.isEmpty() -> CleanerEmptyState(
                icon = "check_circle",
                title = "All Reviewed!",
                subtitle = "No more screenshots to clean. You're all caught up!",
                onButtonClick = onBack
            )
            uiState.isGridView -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.items, key = { it.path }) { item ->
                        val isSelected = selectedPaths.contains(item.path)
                        val isStarred = FavoritesManager.isStarred(context, item.path)

                        Card(
                            onClick = { vm.onToggleSelected(item.path) },
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
                                        onCheckedChange = { vm.onToggleSelected(item.path) },
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
                            Text(
                                text = "${uiState.currentIndex + 1} of ${uiState.items.size} remaining",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                for (stackIdx in minOf(uiState.currentIndex + 2, uiState.items.size - 1) downTo uiState.currentIndex + 1) {
                                    val depth = stackIdx - uiState.currentIndex
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
                                            file = uiState.items[stackIdx].file,
                                            contentDescription = uiState.items[stackIdx].name
                                        )
                                    }
                                }

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
                                                            vm.onKeep()
                                                            animOffsetX.snapTo(0f)
                                                        } else if (offsetX < -120f) {
                                                            animOffsetX.animateTo(-1200f, spring(stiffness = Spring.StiffnessMedium))
                                                            vm.onDelete()
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
                                            vm.onDelete()
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
                                            vm.onKeep()
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
