package com.kd.anddirstat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.GoogleSansFlexFontFamily
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.ExtensionStat
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.ui.components.StorageTrendCard
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.util.StorageTrendManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

data class StorageCategorySummary(
    val name: String,
    val size: Long,
    val color: Color,
    val icon: String
)

data class StorageOverviewData(
    val totalCapacity: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val categories: List<StorageCategorySummary>
)

data class StorageTrendState(
    val history: List<com.kd.anddirstat.util.StorageDayPoint> = emptyList(),
    val recentChanges: List<com.kd.anddirstat.util.StorageChangeItem> = emptyList(),
    val lifetimeFreed: Long = 0L
)

fun calculateStorageOverview(rootNode: CompactNode, totalDeviceSize: Long): StorageOverviewData {
    var appsBytes = 0L
    var videoBytes = 0L
    var imageBytes = 0L
    var audioBytes = 0L
    var docBytes = 0L
    var binBytes = 0L
    var systemOsBytes = 0L
    var tempSystemBytes = 0L
    var otherBytes = 0L
    var freeSpaceBytes = 0L

    val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v")
    val imageExts = setOf("jpg", "jpeg", "png", "webp", "heic", "raw", "svg", "gif", "bmp", "ico")
    val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid")
    val docExts = setOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub")

    val stack = ArrayDeque<Triple<CompactNode, Boolean, Boolean>>()
    stack.add(Triple(rootNode, false, false))

    while (stack.isNotEmpty()) {
        val (node, isInsideAndroid, isInsideTrashed) = stack.removeLast()
        val name = node.name

        if (name == "[Free Space]" || name == "Free Space") {
            freeSpaceBytes += node.size
            continue
        }
        if (name == "[System & OS]" || name == "System & OS") {
            systemOsBytes += node.size
            continue
        }
        if (name == "[Temporary System Files]" || name == "Temporary System Files") {
            tempSystemBytes += node.size
            continue
        }
        if (name == "[Recycle Bin]" || name == "Recycle Bin" || name.startsWith(".trashed") || isInsideTrashed) {
            binBytes += node.size
            continue
        }

        // All app nodes under Apps & System Packages are categorized under Apps (matching Explorer)
        val isApp = node.children?.any { it.name.startsWith("App Code") } == true
        if (isApp || name == "Apps & System Packages") {
            appsBytes += node.size
            continue
        }

        if (!node.isDirectory) {
            val dotIdx = name.lastIndexOf('.')
            val ext = if (dotIdx > 0 && dotIdx < name.length - 1) name.substring(dotIdx + 1).lowercase() else ""

            when {
                ext in videoExts -> videoBytes += node.size
                ext in imageExts -> imageBytes += node.size
                ext in audioExts -> audioBytes += node.size
                ext in docExts -> docBytes += node.size
                isInsideAndroid -> systemOsBytes += node.size
                else -> otherBytes += node.size
            }
        } else {
            val isAndroid = isInsideAndroid || name.equals("Android", ignoreCase = true)
            val isTrashed = isInsideTrashed || name.startsWith(".trashed") || name.equals("Recycle Bin", ignoreCase = true) || name.equals("[Recycle Bin]", ignoreCase = true)
            val children = node.children
            if (children != null) {
                for (i in children.indices.reversed()) {
                    stack.add(Triple(children[i], isAndroid, isTrashed))
                }
            }
        }
    }

    val totalCapacity = if (totalDeviceSize > 0L) totalDeviceSize else rootNode.size
    if (freeSpaceBytes == 0L && totalCapacity > rootNode.size) {
        freeSpaceBytes = totalCapacity - rootNode.size
    }
    val usedSpace = maxOf(0L, totalCapacity - freeSpaceBytes)

    val categories = listOf(
        StorageCategorySummary("Apps", appsBytes, Color(0xFF3B82F6), "apps"),
        StorageCategorySummary("System", systemOsBytes, Color(0xFF94A3B8), "android"),
        StorageCategorySummary("System Temp Files", tempSystemBytes, Color(0xFFF59E0B), "cached"),
        StorageCategorySummary("Videos", videoBytes, Color(0xFFFB923C), "movie"),
        StorageCategorySummary("Images", imageBytes, Color(0xFF34D399), "image"),
        StorageCategorySummary("Audio", audioBytes, Color(0xFFC084FC), "music_note"),
        StorageCategorySummary("Documents", docBytes, Color(0xFF38BDF8), "description"),
        StorageCategorySummary("Recycle Bin", binBytes, Color(0xFFF43F5E), "delete"),
        StorageCategorySummary("Other", otherBytes, Color(0xFFFACC15), "folder_zip")
    ).filter { it.size > 0L }.sortedByDescending { it.size }

    return StorageOverviewData(
        totalCapacity = totalCapacity,
        freeSpace = freeSpaceBytes,
        usedSpace = usedSpace,
        categories = categories
    )
}

data class FileCategoryGroup(
    val id: String,
    val name: String,
    val icon: String,
    val color: Color,
    val matchingExtensions: Set<String>,
    val stats: List<ExtensionStat>,
    val totalSize: Long,
    val fileCount: Int
)

@Composable
fun FileTypesView(
    rootNode: CompactNode,
    stats: List<ExtensionStat>,
    totalDeviceSize: Long,
    onNodeClick: (CompactNode, String) -> Unit = { _, _ -> },
    onNavigateTo: ((String) -> Unit)? = null,
    onDismissPopup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    var expandedCategories by remember(rootNode) { mutableStateOf(setOf<String>()) }
    var expandedExtensions by remember(rootNode) { mutableStateOf(setOf<String>()) }
    val initialOverview = remember(rootNode, totalDeviceSize) {
        StorageOverviewData(
            totalCapacity = if (totalDeviceSize > 0L) totalDeviceSize else rootNode.size,
            freeSpace = 0L,
            usedSpace = rootNode.size,
            categories = emptyList()
        )
    }
    val overview by produceState(initialValue = initialOverview, rootNode, totalDeviceSize) {
        value = withContext(Dispatchers.Default) {
            calculateStorageOverview(rootNode, totalDeviceSize)
        }
    }
    val usedPercent = if (overview.totalCapacity > 0L) (overview.usedSpace.toDouble() / overview.totalCapacity.toDouble() * 100.0) else 0.0

    val visibleStats = remember(stats) {
        stats.filter {
            val name = it.extension.lowercase().trim().removePrefix(".")
            name != "[system & os]" && name != "system & os" &&
            name != "[free space]" && name != "free space" &&
            name != "cache" && name != "app cache" &&
            name != "data" && name != "app data"
        }
    }

    val videoExts = remember { setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v", "mpg", "mpeg", "vob") }
    val imageExts = remember { setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "raw", "svg", "gif", "bmp", "ico", "dng", "cr2", "nef") }
    val audioExts = remember { setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid", "midi", "alac", "amr") }
    val docExts = remember { setOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub", "mobi", "log", "rtf", "html", "htm", "json", "xml", "md", "yaml", "yml") }
    val appExts = remember { setOf("apk", "xapk", "apks", "aab", "obb") }
    val archiveExts = remember { setOf("zip", "rar", "7z", "tar", "gz", "iso", "bin", "img", "dmg", "xz", "bz2", "tgz") }
    val binExts = remember { setOf("[trashed]", "trashed") }

    val appsNode = remember(rootNode) {
        val stack = ArrayDeque<CompactNode>()
        stack.add(rootNode)
        var found: CompactNode? = null
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.name == "Apps & System Packages") {
                found = n
                break
            }
            n.children?.forEach { stack.add(it) }
        }
        found
    }

    val systemNode = remember(rootNode) {
        val stack = ArrayDeque<CompactNode>()
        stack.add(rootNode)
        var found: CompactNode? = null
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.name == "[System & OS]" || n.name == "System & OS") {
                found = n
                break
            }
            n.children?.forEach { stack.add(it) }
        }
        found
    }

    val tempSystemNode = remember(rootNode) {
        val stack = ArrayDeque<CompactNode>()
        stack.add(rootNode)
        var found: CompactNode? = null
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.name == "[Temporary System Files]" || n.name == "Temporary System Files") {
                found = n
                break
            }
            n.children?.forEach { stack.add(it) }
        }
        found
    }

    val categoryGroups = remember(visibleStats, appsNode, systemNode, tempSystemNode) {
        val assignedStats = mutableSetOf<ExtensionStat>()

        fun getStatsForSet(extSet: Set<String>): List<ExtensionStat> {
            val matched = visibleStats.filter { stat ->
                val ext = stat.extension.lowercase().removePrefix(".")
                ext in extSet || stat.extension.lowercase() in extSet
            }
            assignedStats.addAll(matched)
            return matched.sortedByDescending { it.totalSize }
        }

        val bin = getStatsForSet(binExts)
        val videos = getStatsForSet(videoExts)
        val images = getStatsForSet(imageExts)
        val audio = getStatsForSet(audioExts)
        val docs = getStatsForSet(docExts)

        val fileAppStats = getStatsForSet(appExts)
        val combinedAppsStats = mutableListOf<ExtensionStat>()
        if (appsNode != null && appsNode.size > 0L) {
            combinedAppsStats.add(
                ExtensionStat(
                    extension = "Installed Apps",
                    totalSize = appsNode.size,
                    count = appsNode.children?.size ?: 0,
                    color = Color(0xFF3B82F6),
                    category = "App Package"
                )
            )
        }
        combinedAppsStats.addAll(fileAppStats)
        val apps = combinedAppsStats.sortedByDescending { it.totalSize }

        val systemStats = mutableListOf<ExtensionStat>()
        if (systemNode != null && systemNode.size > 0L) {
            systemStats.add(
                ExtensionStat(
                    extension = "System & OS",
                    totalSize = systemNode.size,
                    count = 1,
                    color = Color(0xFF94A3B8),
                    category = "System & OS"
                )
            )
        }
        if (tempSystemNode != null && tempSystemNode.size > 0L) {
            systemStats.add(
                ExtensionStat(
                    extension = "Temporary System Files",
                    totalSize = tempSystemNode.size,
                    count = 1,
                    color = Color(0xFFF59E0B),
                    category = "System & OS"
                )
            )
        }

        val archives = getStatsForSet(archiveExts)
        val others = visibleStats.filter { it !in assignedStats }.sortedByDescending { it.totalSize }

        listOf(
            FileCategoryGroup("system", "System & OS", "android", Color(0xFF94A3B8), emptySet(), systemStats, systemStats.sumOf { it.totalSize }, systemStats.sumOf { it.count }),
            FileCategoryGroup("apps", "Apps & Packages", "apps", Color(0xFF3B82F6), appExts, apps, apps.sumOf { it.totalSize }, apps.sumOf { it.count }),
            FileCategoryGroup("bin", "Recycle Bin", "delete", Color(0xFFF43F5E), binExts, bin, bin.sumOf { it.totalSize }, bin.sumOf { it.count }),
            FileCategoryGroup("videos", "Videos", "movie", Color(0xFFFB923C), videoExts, videos, videos.sumOf { it.totalSize }, videos.sumOf { it.count }),
            FileCategoryGroup("images", "Images", "image", Color(0xFF34D399), imageExts, images, images.sumOf { it.totalSize }, images.sumOf { it.count }),
            FileCategoryGroup("audio", "Audio", "music_note", Color(0xFFC084FC), audioExts, audio, audio.sumOf { it.totalSize }, audio.sumOf { it.count }),
            FileCategoryGroup("docs", "Documents", "description", Color(0xFF38BDF8), docExts, docs, docs.sumOf { it.totalSize }, docs.sumOf { it.count }),
            FileCategoryGroup("archives", "Archives & Disk Images", "archive", Color(0xFFF59E0B), archiveExts, archives, archives.sumOf { it.totalSize }, archives.sumOf { it.count }),
            FileCategoryGroup("others", "Other Files", "folder", Color(0xFF94A3B8), emptySet(), others, others.sumOf { it.totalSize }, others.sumOf { it.count })
        ).filter { it.totalSize > 0L || it.fileCount > 0 }
            .sortedByDescending { it.totalSize }
    }

    fun collectFilesForExtension(
        root: CompactNode,
        targetExt: String,
        rootPath: String,
        outList: ArrayList<Pair<CompactNode, String>>,
        maxLimit: Int = 1000
    ) {
        val cleanTarget = targetExt.lowercase().removePrefix(".").removePrefix("[").removeSuffix("]").trim()
        val isTargetBin = cleanTarget == "trashed" || cleanTarget == "recycle bin"
        val isAppsTarget = cleanTarget == "installed apps" || cleanTarget == "apps"

        if (cleanTarget == "system & os" || cleanTarget == "system") {
            if (systemNode != null) {
                outList.add(systemNode to "System & OS")
            }
            return
        }

        if (cleanTarget == "temporary system files" || cleanTarget == "system temp files" || cleanTarget == "temp files") {
            if (tempSystemNode != null) {
                outList.add(tempSystemNode to "Temporary System Files")
            }
            return
        }

        if (isAppsTarget) {
            appsNode?.children?.sortedByDescending { it.size }?.forEach { appChild ->
                outList.add(appChild to "Apps & System Packages/${appChild.name}")
            }
            return
        }

        val stack = ArrayDeque<Pair<CompactNode, String>>()
        stack.add(root to rootPath)

        while (stack.isNotEmpty() && outList.size < maxLimit) {
            val (node, currentPath) = stack.removeLast()
            val name = node.name

            if (name == "Cache" || name == "App Cache" || name == "Data" || name == "App Data" ||
                name == "[Free Space]" || name == "[System & OS]") {
                continue
            }

            val isTrashed = name.startsWith(".trashed") || name == "[Recycle Bin]" || currentPath.contains("[Recycle Bin]") || currentPath.contains(".trashed")
            if (isTargetBin) {
                if (!node.isDirectory && isTrashed) {
                    outList.add(node to currentPath)
                }
            } else {
                if (isTrashed) {
                    continue
                }

                val isApp = node.children?.any { it.name.startsWith("App Code") } == true
                if (isApp || name.startsWith("App Code") || name == "Apps & System Packages" || currentPath.startsWith("Apps & System Packages")) {
                    continue
                }
                val ext = if (name.endsWith(".apk", ignoreCase = true) || name.endsWith(".apks", ignoreCase = true) || name.endsWith(".xapk", ignoreCase = true) || name.endsWith(".obb", ignoreCase = true)) {
                    "apk"
                } else {
                    val dotIdx = name.lastIndexOf('.')
                    if (dotIdx > 0 && dotIdx < name.length - 1) name.substring(dotIdx + 1).lowercase() else ""
                }

                val isMatch = if (cleanTarget == "[no ext]" || cleanTarget == "no ext" || cleanTarget.isEmpty()) {
                    ext.isEmpty()
                } else {
                    ext == cleanTarget
                }

                if (!node.isDirectory && isMatch) {
                    outList.add(node to currentPath)
                }
            }

            val children = node.children
            if (children != null) {
                for (i in children.indices.reversed()) {
                    val child = children[i]
                    val childPath = if (currentPath == "Device Storage") child.name
                        else if (currentPath.endsWith("/")) "$currentPath${child.name}"
                        else "$currentPath/${child.name}"
                    stack.add(child to childPath)
                }
            }
        }
    }

    val filesByExt by produceState(
        initialValue = emptyMap<String, List<Pair<CompactNode, String>>>(),
        key1 = rootNode,
        key2 = expandedExtensions
    ) {
        if (expandedExtensions.isEmpty()) {
            value = emptyMap()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            expandedExtensions.associateWith { ext ->
                val list = ArrayList<Pair<CompactNode, String>>()
                collectFilesForExtension(rootNode, ext, rootNode.name, list)
                list.sortedByDescending { it.first.size }
            }
        }
    }

    val extFileLimits = remember(rootNode) { mutableStateMapOf<String, Int>() }
    val catStatsLimits = remember(rootNode) { mutableStateMapOf<String, Int>() }

    var hasAnimatedUsage by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) onDismissPopup()
    }

    val heroBarProgress = remember { Animatable(0f) }
    LaunchedEffect(overview.usedSpace, overview.totalCapacity) {
        if (overview.usedSpace > 0L) {
            StorageTrendManager.recordSnapshot(context, overview.usedSpace, overview.totalCapacity)
            heroBarProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            )
        }
    }

    val trendState by produceState(
        initialValue = StorageTrendState(),
        key1 = rootNode,
        key2 = overview.usedSpace,
        key3 = overview.totalCapacity
    ) {
        value = withContext(Dispatchers.IO) {
            val hist = StorageTrendManager.getHistory(context, overview.usedSpace, overview.totalCapacity)
            val changes = StorageTrendManager.findRecentChanges(rootNode, limit = 5)
            val freed = StorageTrendManager.getLifetimeFreedBytes(context)
            StorageTrendState(hist, changes, freed)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 16.dp, bottom = 116.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Merged Header: Big "X of Y used" + Free space + Proportional bar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                val usedStr = FileUtils.formatFileSize(overview.usedSpace)
                val totalStr = FileUtils.formatFileSize(overview.totalCapacity)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleLarge.fontSize, fontWeight = FontWeight.Bold)) {
                            append(usedStr)
                        }
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("/$totalStr")
                        }
                    },
                    fontFamily = GoogleSansFlexFontFamily,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Proportional Multi-Colored Segmented Bar with reduced width and % at end
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = heroBarProgress.value)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                            ) {
                                if (overview.totalCapacity > 0L) {
                                    overview.categories.forEach { cat ->
                                        val weight = (cat.size.toFloat() / overview.totalCapacity.toFloat()).coerceAtLeast(0.005f)
                                        Box(
                                            modifier = Modifier
                                                .weight(weight)
                                                .fillMaxHeight()
                                                .background(cat.color)
                                        )
                                    }
                                    if (overview.freeSpace > 0L) {
                                        val freeWeight = (overview.freeSpace.toFloat() / overview.totalCapacity.toFloat()).coerceAtLeast(0.005f)
                                        Box(
                                            modifier = Modifier
                                                .weight(freeWeight)
                                                .fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    val displayUsedPercent = usedPercent * heroBarProgress.value
                    Text(
                        text = String.format(Locale.US, "%.0f%%", displayUsedPercent),
                        fontFamily = GoogleSansFlexFontFamily,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Main Categories List with Sub-File Types
        val usedStorageTotal = if (overview.usedSpace > 0L) overview.usedSpace else overview.totalCapacity

        categoryGroups.forEachIndexed { catIndex, category ->
            val isBinCategory = category.id == "bin"
            val isSystemCategory = category.id == "system" || category.name.equals("System & OS", ignoreCase = true)
            val isNonExpandable = isBinCategory || isSystemCategory
            val isCatExpanded = !isNonExpandable && expandedCategories.contains(category.id)
            val catFraction = if (usedStorageTotal > 0L) (category.totalSize.toDouble() / usedStorageTotal.toDouble()).coerceIn(0.0, 1.0) else 0.0

            val onCategoryClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (isBinCategory) {
                    onNavigateTo?.invoke(com.kd.anddirstat.model.AppDestinations.CLEANER_RECYCLE_BIN)
                } else if (!isNonExpandable) {
                    expandedCategories = if (isCatExpanded) expandedCategories - category.id else expandedCategories + category.id
                }
            }

            item(key = "cat_${category.id}") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isNonExpandable) {
                                    val catArrowRotation by animateFloatAsState(
                                        targetValue = if (isCatExpanded) 90f else 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        label = "catChevronRotation"
                                    )
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .clickable { onCategoryClick() }
                                    ) {
                                        MaterialSymbol(
                                            name = "chevron_right",
                                            active = true,
                                            size = 18.dp,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.graphicsLayer { rotationZ = catArrowRotation }
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))
                                } else {
                                    Spacer(modifier = Modifier.width(26.dp))
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .clickable { onCategoryClick() }
                                ) {
                                    MaterialSymbol(
                                        name = category.icon,
                                        active = true,
                                        size = 24.dp,
                                        tint = category.color
                                    )
                                }
                            }
                        },
                        headlineContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category.name,
                                    fontFamily = GoogleSansFlexFontFamily,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = FileUtils.formatFileSize(category.totalSize),
                                    fontFamily = GoogleSansFlexFontFamily,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = category.color
                                )
                            }
                        },
                        supportingContent = {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when {
                                            isBinCategory -> "Tap to view and clean recycled files"
                                            isSystemCategory -> "Android OS and system runtime data"
                                            else -> "${category.stats.size} types • ${category.fileCount} files"
                                        },
                                        fontFamily = GoogleSansFlexFontFamily,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val displayCatPercent = catFraction * 100.0
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", displayCatPercent),
                                        fontFamily = GoogleSansFlexFontFamily,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { catFraction.toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(CircleShape),
                                    color = category.color,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        },
                        trailingContent = if (isBinCategory) {
                            {
                                MaterialSymbol(
                                    name = "chevron_right",
                                    active = true,
                                    size = 22.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else null,
                        colors = ListItemDefaults.colors(
                            containerColor = if (isCatExpanded) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f) else Color.Transparent
                        ),
                        modifier = Modifier.clickable { onCategoryClick() }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // Inner contents slide down smoothly when expanding
                    AnimatedVisibility(
                        visible = isCatExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val catLimit = catStatsLimits[category.id] ?: 15
                            val displayStats = if (category.stats.size > catLimit) category.stats.take(catLimit) else category.stats

                            displayStats.forEachIndexed { statIndex, stat ->
                                val statFraction = if (category.totalSize > 0L) (stat.totalSize.toDouble() / category.totalSize.toDouble()).coerceIn(0.0, 1.0) else 0.0
                                val iconColor = FileUtils.getFileTypeIconColor(stat.extension, isDark)
                                val hasFiles = stat.count > 0
                                val isStatExpanded = hasFiles && expandedExtensions.contains(stat.extension)

                                val extBarProgress = remember(stat.extension) { Animatable(1f) }

                                ListItem(
                                    leadingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(modifier = Modifier.width(16.dp))
                                            if (hasFiles) {
                                                val extArrowRotation by animateFloatAsState(
                                                    targetValue = if (isStatExpanded) 90f else 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    ),
                                                    label = "extChevronRotation"
                                                )
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            expandedExtensions = if (isStatExpanded) expandedExtensions - stat.extension else expandedExtensions + stat.extension
                                                        }
                                                ) {
                                                    MaterialSymbol(
                                                        name = "chevron_right",
                                                        active = true,
                                                        size = 18.dp,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.graphicsLayer { rotationZ = extArrowRotation }
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(2.dp))
                                            } else {
                                                Spacer(modifier = Modifier.width(26.dp))
                                            }

                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        if (hasFiles) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            expandedExtensions = if (isStatExpanded) expandedExtensions - stat.extension else expandedExtensions + stat.extension
                                                        }
                                                    }
                                            ) {
                                                val extSymbol = remember(stat.extension) { FileUtils.getExtensionSymbolName(stat.extension) }
                                                MaterialSymbol(
                                                    name = extSymbol,
                                                    active = true,
                                                    size = 24.dp,
                                                    tint = iconColor
                                                )
                                            }
                                        }
                                    },
                                    headlineContent = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val displayName = remember(stat.extension) {
                                                val clean = stat.extension.removePrefix("[").removeSuffix("]").trim()
                                                when {
                                                    clean.equals("trashed", ignoreCase = true) -> "Trashed Files"
                                                    clean.equals("system & os", ignoreCase = true) -> "System & OS"
                                                    clean.equals("temporary system files", ignoreCase = true) -> "Temporary System Files"
                                                    clean.equals("no ext", ignoreCase = true) -> "No Extension"
                                                    clean.equals("installed apps", ignoreCase = true) -> "Installed Apps"
                                                    clean.startsWith(".") -> clean
                                                    else -> clean
                                                }
                                            }
                                            Text(
                                                text = displayName,
                                                fontFamily = GoogleSansFlexFontFamily,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = FileUtils.formatFileSize(stat.totalSize),
                                                fontFamily = GoogleSansFlexFontFamily,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    supportingContent = {
                                        Column(modifier = Modifier.padding(top = 2.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${stat.count} files",
                                                    fontFamily = GoogleSansFlexFontFamily,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                val displayExtPercent = statFraction * 100.0 * extBarProgress.value
                                                Text(
                                                    text = String.format(Locale.US, "%.1f%%", displayExtPercent),
                                                    fontFamily = GoogleSansFlexFontFamily,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            LinearProgressIndicator(
                                                progress = { (statFraction.toFloat() * extBarProgress.value).coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                                    .clip(CircleShape),
                                                color = iconColor,
                                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                strokeCap = StrokeCap.Round
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = if (isStatExpanded) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f) else Color.Transparent
                                    ),
                                    modifier = if (hasFiles) {
                                        Modifier.clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            expandedExtensions = if (isStatExpanded) expandedExtensions - stat.extension else expandedExtensions + stat.extension
                                        }
                                    } else Modifier
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(start = 32.dp)
                                )

                                // Individual Files under this Extension slide down when expanding
                                AnimatedVisibility(
                                    visible = isStatExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        val files = filesByExt[stat.extension] ?: emptyList()
                                        val fileLimit = extFileLimits[stat.extension] ?: 20
                                        val displayFiles = if (files.size > fileLimit) files.take(fileLimit) else files

                                        if (files.isEmpty() && stat.count > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            }
                                        } else {
                                            displayFiles.forEach { (fileNode, filePath) ->
                                                val fileFraction = if (stat.totalSize > 0L) (fileNode.size.toDouble() / stat.totalSize.toDouble()).coerceIn(0.0, 1.0) else 0.0
                                                val fileColor = FileUtils.getNodeIconColor(fileNode, isDark)
                                                val isApp = fileNode.children?.any { it.name.startsWith("App Code") } == true
                                                val appPkg = if (isApp) FileUtils.extractPackageName(fileNode) else null
                                                val isMedia = remember(fileNode.name) {
                                                    val l = fileNode.name.lowercase()
                                                    l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") ||
                                                    l.endsWith(".heic") || l.endsWith(".gif") || l.endsWith(".mp4") || l.endsWith(".mkv") ||
                                                    l.endsWith(".avi") || l.endsWith(".mov") || l.endsWith(".webm") || l.endsWith(".3gp") ||
                                                    l.endsWith(".apk")
                                                }

                                                ListItem(
                                                    leadingContent = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Spacer(modifier = Modifier.width(44.dp))
                                                            Box(
                                                                contentAlignment = Alignment.Center,
                                                                modifier = Modifier.size(36.dp)
                                                            ) {
                                                                if (isMedia) {
                                                                    MediaThumbnailView(
                                                                        node = fileNode,
                                                                        path = filePath,
                                                                        fallbackTint = fileColor,
                                                                        modifier = Modifier
                                                                            .size(32.dp)
                                                                            .clip(RoundedCornerShape(6.dp))
                                                                    )
                                                                } else if (appPkg != null) {
                                                                    AppIconView(
                                                                        packageName = appPkg,
                                                                        contentDescription = fileNode.name,
                                                                        modifier = Modifier
                                                                            .size(28.dp)
                                                                            .clip(RoundedCornerShape(6.dp))
                                                                    )
                                                                } else {
                                                                    val fileSymbol = remember(fileNode, isApp) { FileUtils.getNodeSymbolName(fileNode, isApp) }
                                                                    MaterialSymbol(
                                                                        name = fileSymbol,
                                                                        active = true,
                                                                        size = 22.dp,
                                                                        tint = fileColor
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    },
                                                    headlineContent = {
                                                        Text(
                                                            text = fileNode.name,
                                                            fontFamily = GoogleSansFlexFontFamily,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    },
                                                    supportingContent = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = FileUtils.formatFileSize(fileNode.size),
                                                                fontFamily = GoogleSansFlexFontFamily,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f, fill = false),
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = String.format(Locale.US, "%.1f%%", fileFraction * 100.0),
                                                                fontFamily = GoogleSansFlexFontFamily,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                                    modifier = Modifier.clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onNodeClick(fileNode, filePath)
                                                    }
                                                )
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f),
                                                    modifier = Modifier.padding(start = 72.dp)
                                                )
                                            }

                                            // Load More for huge file collections
                                            if (files.size > fileLimit) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 44.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Showing $fileLimit of ${if (stat.count > files.size) stat.count else files.size} files",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        TextButton(
                                                            onClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                extFileLimits[stat.extension] = fileLimit + 30
                                                            }
                                                        ) {
                                                            Text("+30 more", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                        }
                                                        TextButton(
                                                            onClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                extFileLimits[stat.extension] = files.size
                                                            }
                                                        ) {
                                                            Text("Show All", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (category.stats.size > catLimit) {
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        catStatsLimits[category.id] = category.stats.size
                                    },
                                    modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text("Show all ${category.stats.size} types", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "storage_trend_section") {
            if (trendState.history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(16.dp))

                StorageTrendCard(
                    history = trendState.history,
                    recentChanges = trendState.recentChanges,
                    lifetimeFreed = trendState.lifetimeFreed,
                    onItemClick = onNodeClick,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
