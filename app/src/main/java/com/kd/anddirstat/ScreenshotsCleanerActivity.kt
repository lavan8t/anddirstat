package com.kd.anddirstat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ScreenshotSort(val label: String, val icon: String) {
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

fun getScreenshotAspectRatio(file: File): Float {
    return try {
        val cached = MediaThumbnailCache.get(file.absolutePath)
        if (cached != null && cached.height > 0) {
            (cached.width.toFloat() / cached.height.toFloat()).coerceIn(0.4f, 2.5f)
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outHeight > 0) {
                (options.outWidth.toFloat() / options.outHeight.toFloat()).coerceIn(0.4f, 2.5f)
            } else {
                9f / 16f
            }
        }
    } catch (_: Exception) {
        9f / 16f
    }
}

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
                size = 32.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }
    }
}

class ScreenshotsCleanerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }
        enableEdgeToEdge()

        setContent {
            AndDirStatAppTheme {
                ScreenshotsCleanerView(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotsCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var screenshots by remember { mutableStateOf<List<ScreenshotItem>>(emptyList()) }
    var totalInitialCount by remember { mutableIntStateOf(0) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isImageLoading by remember { mutableStateOf(false) }

    var sortOrder by remember { mutableStateOf(ScreenshotSort.OLDEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    fun sortScreenshotList(list: List<ScreenshotItem>, sort: ScreenshotSort): List<ScreenshotItem> {
        return when (sort) {
            ScreenshotSort.NEWEST -> list.sortedByDescending { it.file.lastModified() }
            ScreenshotSort.OLDEST -> list.sortedBy { it.file.lastModified() }
            ScreenshotSort.LARGEST -> list.sortedByDescending { it.size }
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            val items = mutableListOf<ScreenshotItem>()
            val root = TreeCacheManager.loadTree(context)
            if (root != null) {
                val list = StorageFilterHelper.getScreenshots(root, limit = 500)
                for (entry in list) {
                    val f = FileUtils.resolveActualFile(entry.path) ?: FileUtils.resolveActualFile(entry.node.name)
                    if (f != null && f.exists() && f.canRead()) {
                        items.add(ScreenshotItem(file = f, name = entry.node.name, size = entry.node.size, path = entry.path))
                    }
                }
            }

            if (items.isEmpty()) {
                val dirs = listOf(
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots")
                )
                for (dir in dirs) {
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.filter { !it.isDirectory && it.canRead() }?.forEach { f ->
                            val name = f.name.lowercase()
                            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".heic")) {
                                items.add(ScreenshotItem(file = f, name = f.name, size = f.length(), path = f.absolutePath))
                            }
                        }
                    }
                }
            }
            items.distinctBy { it.file.absolutePath }.sortedBy { it.file.lastModified() }
        }
        screenshots = loaded
        totalInitialCount = loaded.size
        isLoading = false
    }

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

    val currentItem = screenshots.getOrNull(currentIndex)

    // Preload current and next 5 screenshots for zero-lag 60fps swiping
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
                            if (bmp != null) {
                                MediaThumbnailCache.put(file.absolutePath, bmp)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    fun proceedToNext() {
        if (currentIndex < screenshots.size - 1) {
            currentIndex++
        } else {
            screenshots = emptyList()
        }
    }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(Unit) {
        com.kd.anddirstat.util.AppNotifier.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun handleDelete() {
        val itemToDelete = currentItem ?: return
        val fileToDelete = itemToDelete.file
        val deletedIndex = currentIndex

        if (FavoritesManager.isStarred(context, itemToDelete.path)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            com.kd.anddirstat.util.AppNotifier.notify("Starred screenshot is protected. Unstar it manually first to delete.")
            return
        }

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val nextList = screenshots.toMutableList()
        nextList.removeAt(deletedIndex)
        screenshots = nextList
        if (currentIndex >= screenshots.size) {
            currentIndex = maxOf(0, screenshots.size - 1)
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    FileUtils.deleteOrTrashFile(fileToDelete, context)
                } catch (_: Exception) {}
            }
            val snackResult = snackbarHostState.showSnackbar(
                message = "Moved ${itemToDelete.name} to Recycle Bin",
                actionLabel = "Undo",
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            if (snackResult == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                withContext(Dispatchers.IO) {
                    try {
                        FileUtils.restoreTrashedFile(fileToDelete, context)
                    } catch (_: Exception) {}
                }
                val restoredList = screenshots.toMutableList()
                val insertIndex = minOf(deletedIndex, restoredList.size)
                restoredList.add(insertIndex, itemToDelete)
                screenshots = restoredList
                currentIndex = insertIndex
                com.kd.anddirstat.util.AppNotifier.notify("Restored ${itemToDelete.name}")
            }
        }
    }

    fun handleKeep() {
        if (currentItem == null) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        proceedToNext()
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Screenshots Cleaner",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isLoading && screenshots.isNotEmpty()) {
                            Text(
                                text = "${screenshots.size} of $totalInitialCount remaining",
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
                    if (!isLoading && screenshots.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                MaterialSymbol("sort", active = true, size = 22.dp)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                ScreenshotSort.entries.forEach { sort ->
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
                                            screenshots = sortScreenshotList(screenshots, sort)
                                            currentIndex = 0
                                        }
                                    )
                                }
                            }
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                screenshots.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialSymbol("check_circle", active = true, size = 40.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "All Reviewed!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No screenshots left to review.",
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
                currentItem != null -> {
                    val animOffsetX = remember(currentItem.file.absolutePath) { Animatable(0f) }
                    var isDragging by remember(currentItem.file.absolutePath) { mutableStateOf(false) }

                    // Continuous tactile vibration when holding in Delete (Left) or Keep (Right) zones
                    LaunchedEffect(isDragging, animOffsetX.value) {
                        if (isDragging) {
                            val offset = animOffsetX.value
                            if (offset < -120f) {
                                while (isActive && isDragging && animOffsetX.value < -120f) {
                                    triggerVibration(isDelete = true)
                                    delay(70)
                                }
                            } else if (offset > 120f) {
                                while (isActive && isDragging && animOffsetX.value > 120f) {
                                    triggerVibration(isDelete = false)
                                    delay(85)
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 560.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        val topAspectRatio = remember(currentItem.file.absolutePath) { getScreenshotAspectRatio(currentItem.file) }

                        // Stack Container with up to 3 cards ready behind the top card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            val dragRatio = (abs(animOffsetX.value) / 160f).coerceIn(0f, 1f)

                            // Render 3 stacked background cards (indices 3, 2, 1)
                            for (stackOffset in 3 downTo 1) {
                                val itemIdx = currentIndex + stackOffset
                                if (itemIdx < screenshots.size) {
                                    val itemFile = screenshots[itemIdx].file
                                    val itemAspectRatio = remember(itemFile.absolutePath) { getScreenshotAspectRatio(itemFile) }
                                    val baseScale = 1f - stackOffset * 0.045f
                                    val targetScale = 1f - (stackOffset - 1) * 0.045f
                                    val cardScale = baseScale + (targetScale - baseScale) * dragRatio

                                    val baseOffsetY = (stackOffset * 10).dp
                                    val targetOffsetY = ((stackOffset - 1) * 10).dp
                                    val cardOffsetY = baseOffsetY - (baseOffsetY - targetOffsetY) * dragRatio

                                    val baseAlpha = (1f - stackOffset * 0.22f).coerceAtLeast(0.35f)
                                    val targetAlpha = (1f - (stackOffset - 1) * 0.22f).coerceAtLeast(0.35f)
                                    val cardAlpha = baseAlpha + (targetAlpha - baseAlpha) * dragRatio

                                    key(itemFile.absolutePath) {
                                        Surface(
                                            shape = RoundedCornerShape(24.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            tonalElevation = (4 - stackOffset).dp,
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .aspectRatio(itemAspectRatio, matchHeightConstraintsFirst = true)
                                                .offset(y = cardOffsetY)
                                                .graphicsLayer {
                                                    scaleX = cardScale
                                                    scaleY = cardScale
                                                    alpha = cardAlpha
                                                }
                                        ) {
                                            StackScreenshotImage(
                                                file = itemFile,
                                                contentDescription = screenshots[itemIdx].name
                                            )
                                        }
                                    }
                                }
                            }

                            // Active Top Draggable Screenshot
                            key(currentItem.file.absolutePath) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(topAspectRatio, matchHeightConstraintsFirst = true)
                                        .offset { IntOffset(animOffsetX.value.roundToInt(), 0) }
                                        .graphicsLayer {
                                            rotationZ = (animOffsetX.value / 25f).coerceIn(-12f, 12f)
                                        }
                                        .clip(RoundedCornerShape(24.dp))
                                        .pointerInput(currentItem.file.absolutePath) {
                                            detectHorizontalDragGestures(
                                                onDragStart = { isDragging = true },
                                                onDragCancel = {
                                                    isDragging = false
                                                    scope.launch {
                                                        animOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                                    }
                                                },
                                                onDragEnd = {
                                                    isDragging = false
                                                    scope.launch {
                                                        if (animOffsetX.value < -160f) {
                                                            animOffsetX.animateTo(-800f, tween(150, easing = FastOutSlowInEasing))
                                                            animOffsetX.snapTo(0f)
                                                            handleDelete()
                                                        } else if (animOffsetX.value > 160f) {
                                                            animOffsetX.animateTo(800f, tween(150, easing = FastOutSlowInEasing))
                                                            animOffsetX.snapTo(0f)
                                                            handleKeep()
                                                        } else {
                                                            animOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                                        }
                                                    }
                                                },
                                                onHorizontalDrag = { _, dragAmount ->
                                                    isDragging = true
                                                    scope.launch {
                                                        animOffsetX.snapTo(animOffsetX.value + dragAmount)
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    StackScreenshotImage(
                                        file = currentItem.file,
                                        contentDescription = currentItem.name
                                    )

                                    // Swipe Trash Overlay (Clipped with screenshot shape)
                                    val leftAlpha = (-animOffsetX.value / 150f).coerceIn(0f, 0.85f)
                                    if (leftAlpha > 0.05f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.error.copy(alpha = leftAlpha)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                MaterialSymbol("delete", active = true, size = 42.dp, tint = MaterialTheme.colorScheme.onError)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("DELETE", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onError)
                                            }
                                        }
                                    }

                                    // Swipe Keep Overlay (Clipped with screenshot shape)
                                    val rightAlpha = (animOffsetX.value / 150f).coerceIn(0f, 0.85f)
                                    if (rightAlpha > 0.05f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = rightAlpha)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                MaterialSymbol("check", active = true, size = 42.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("KEEP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Details OUTSIDE the image: File Name & Size alone (No path)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
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
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = FileUtils.formatFileSize(currentItem.size, context),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons: Delete on top, Keep below. All fully rounded (28dp) and thicc (54dp)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { handleDelete() },
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MaterialSymbol("delete", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onError)
                                    Text("Delete", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }

                            FilledTonalButton(
                                onClick = { handleKeep() },
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MaterialSymbol("check", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
                                    Text("Don't Delete (Keep)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
