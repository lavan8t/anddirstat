package com.kd.anddirstat.ui.screens

import android.view.Choreographer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.treemap.TreemapCanvas
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random

/**
 * Generates a mock hierarchical storage tree containing exactly [targetCount] files
 * with a realistic scatter of categories, depths, and file sizes.
 */
fun generate50kScatterTree(targetCount: Int = 50_000, seed: Long = 42L): CompactNode {
    val rng = Random(seed)

    val extensions = listOf(
        // Video
        "mp4", "mkv", "avi", "mov", "webm",
        // Audio
        "mp3", "flac", "wav", "m4a", "ogg",
        // Images
        "jpg", "jpeg", "png", "webp", "heic", "raw",
        // Documents
        "pdf", "docx", "xlsx", "txt", "epub", "md",
        // Archives
        "zip", "rar", "7z", "tar.gz",
        // Apps / Binaries
        "apk", "dex", "so", "bin",
        // Temporary / Cache
        "tmp", "log", "cache", "crdownload", "part"
    )

    val folderNames = listOf(
        "DCIM", "Camera", "Pictures", "Screenshots", "Movies", "Downloads",
        "Documents", "Music", "Podcasts", "Android", "data", "media",
        "Telegram", "WhatsApp", "Backup", "Recordings", "Books", "Projects",
        "RawPhotos", "OfflineCache", "TempData", "Exports", "Ringtones"
    )

    // Distribution of sizes: log-scale distribution
    fun randomFileSize(): Long {
        val r = rng.nextFloat()
        return when {
            r < 0.02f -> (100L * 1024 * 1024) + (rng.nextInt(3000).toLong() * 1024 * 1024) // 100MB - 3.1GB (Huge)
            r < 0.15f -> (10L * 1024 * 1024) + (rng.nextInt(90).toLong() * 1024 * 1024)   // 10MB - 100MB (Large)
            r < 0.55f -> (500L * 1024) + (rng.nextInt(9500).toLong() * 1024)             // 500KB - 10MB (Medium)
            else -> 1024L + rng.nextInt(500 * 1024).toLong()                             // 1KB - 500KB (Small)
        }
    }

    var remainingFiles = targetCount
    val topLevelFolders = ArrayList<CompactNode>()
    var folderIndex = 0

    // Distribute into ~100-150 top-level and sub-level folders
    val numFolders = 80
    val filesPerFolderBase = targetCount / numFolders

    while (remainingFiles > 0) {
        val folderName = if (folderIndex < folderNames.size) {
            folderNames[folderIndex]
        } else {
            "Folder_${folderIndex + 1}"
        }
        folderIndex++

        val filesInThisFolder = if (remainingFiles <= filesPerFolderBase * 2) {
            remainingFiles
        } else {
            (filesPerFolderBase * 0.4f + rng.nextInt((filesPerFolderBase * 1.2f).toInt())).toInt().coerceAtMost(remainingFiles)
        }

        // Split into 2-3 subfolders inside this folder if large
        val subfolderCount = if (filesInThisFolder > 400) rng.nextInt(4) + 1 else 0
        val subfolders = ArrayList<CompactNode>()
        var folderChildrenTotalSize = 0L

        if (subfolderCount > 0) {
            var subRemaining = filesInThisFolder
            for (s in 0 until subfolderCount) {
                val subCount = if (s == subfolderCount - 1) subRemaining else (subRemaining / (subfolderCount - s))
                val subFiles = ArrayList<CompactNode>(subCount)
                var subTotalSize = 0L
                for (i in 0 until subCount) {
                    val ext = extensions[rng.nextInt(extensions.size)]
                    val size = randomFileSize()
                    subTotalSize += size
                    subFiles.add(CompactNode(name = "file_${folderIndex}_${s}_$i.$ext", isDirectory = false, size = size))
                }
                subRemaining -= subCount
                subfolders.add(
                    CompactNode(
                        name = "Sub_${s + 1}",
                        isDirectory = true,
                        size = subTotalSize,
                        children = subFiles.toTypedArray()
                    )
                )
                folderChildrenTotalSize += subTotalSize
            }
        } else {
            val directFiles = ArrayList<CompactNode>(filesInThisFolder)
            for (i in 0 until filesInThisFolder) {
                val ext = extensions[rng.nextInt(extensions.size)]
                val size = randomFileSize()
                folderChildrenTotalSize += size
                directFiles.add(CompactNode(name = "file_${folderIndex}_$i.$ext", isDirectory = false, size = size))
            }
            subfolders.addAll(directFiles)
        }

        topLevelFolders.add(
            CompactNode(
                name = folderName,
                isDirectory = true,
                size = folderChildrenTotalSize,
                children = subfolders.toTypedArray()
            )
        )

        remainingFiles -= filesInThisFolder
    }

    val totalTreeSize = topLevelFolders.sumOf { it.size }
    return CompactNode(
        name = "StressTest50K",
        isDirectory = true,
        size = totalTreeSize,
        children = topLevelFolders.toTypedArray()
    )
}

@Composable
fun TestTreemapScreen(
    onBack: () -> Unit,
    isDark: Boolean = true,
    pureBlack: Boolean = false,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var testSeed by remember { mutableStateOf(System.currentTimeMillis()) }
    var rootNode by remember { mutableStateOf<CompactNode?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var resetKey by remember { mutableIntStateOf(0) }
    var selectedNode by remember { mutableStateOf<CompactNode?>(null) }
    var selectedPath by remember { mutableStateOf<String?>(null) }

    // Live FPS Tracker
    var currentFps by remember { mutableIntStateOf(60) }
    DisposableEffect(Unit) {
        var frameCount = 0
        var lastTime = System.nanoTime()
        var running = true

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                frameCount++
                val elapsed = frameTimeNanos - lastTime
                if (elapsed >= 500_000_000L) { // every 0.5s
                    currentFps = ((frameCount * 1_000_000_000L) / elapsed).toInt()
                    frameCount = 0
                    lastTime = frameTimeNanos
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(callback)

        onDispose {
            running = false
            Choreographer.getInstance().removeFrameCallback(callback)
        }
    }

    // Load or regenerate 50k items
    fun load50k(seed: Long) {
        isLoading = true
        selectedNode = null
        selectedPath = null
        coroutineScope.launch {
            val node = withContext(Dispatchers.Default) {
                generate50kScatterTree(targetCount = 50_000, seed = seed)
            }
            rootNode = node
            isLoading = false
        }
    }

    LaunchedEffect(testSeed) {
        load50k(testSeed)
    }

    val baseBg = if (pureBlack && isDark) Color.Black else if (isDark) Color(0xFF08090E) else Color(0xFFF1F3F9)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
    ) {
        val currentRoot = rootNode
        if (currentRoot != null && !isLoading) {
            TreemapCanvas(
                rootNode = currentRoot,
                rootPath = "StressTest50K",
                selectedNode = selectedNode,
                resetKey = resetKey,
                isDark = isDark,
                pureBlack = pureBlack,
                isLandscape = isLandscape,
                onScaleChanged = { scale = it },
                onNodeSelected = { node, path, _ ->
                    selectedNode = node
                    selectedPath = path
                },
                onDismissPopup = {
                    selectedNode = null
                    selectedPath = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Control & Performance HUD Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    MaterialSymbol(
                        name = "arrow_back",
                        active = true,
                        size = 22.dp,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "50K Items Scatter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (currentFps >= 50) Color(0xFF00C853).copy(alpha = 0.2f)
                                    else if (currentFps >= 30) Color(0xFFFFB300).copy(alpha = 0.2f)
                                    else Color(0xFFFF3D00).copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$currentFps FPS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (currentFps >= 50) Color(0xFF00E676)
                                else if (currentFps >= 30) Color(0xFFFFC107)
                                else Color(0xFFFF5252)
                            )
                        }
                    }

                    Text(
                        text = "Zoom: ${String.format("%.2fx", scale)} • Total: 50,000 files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Reset Zoom button
                if (scale > 1.05f) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            resetKey++
                            scale = 1f
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        MaterialSymbol(
                            name = "restart_alt",
                            active = true,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Reshuffle seed
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        testSeed = System.currentTimeMillis()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    MaterialSymbol(
                        name = "shuffle",
                        active = true,
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseBg.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Generating 50,000 random items...",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Selected Tile Info Pill at Bottom
        AnimatedVisibility(
            visible = selectedNode != null && !isLoading,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            val node = selectedNode
            if (node != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            MaterialSymbol(
                                name = if (node.isDirectory) "folder" else "description",
                                active = true,
                                size = 22.dp,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${FileUtils.formatFileSize(node.size)} • ${selectedPath ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                selectedNode = null
                                selectedPath = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            MaterialSymbol(
                                name = "close",
                                active = true,
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
