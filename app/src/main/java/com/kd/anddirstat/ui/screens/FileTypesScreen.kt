package com.kd.anddirstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.GoogleSansFlexFontFamily
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.ExtensionStat
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.util.FileUtils
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

fun calculateStorageOverview(rootNode: CompactNode, totalDeviceSize: Long): StorageOverviewData {
    var appsBytes = 0L
    var videoBytes = 0L
    var imageBytes = 0L
    var audioBytes = 0L
    var docBytes = 0L
    var binBytes = 0L
    var systemBytes = 0L
    var otherBytes = 0L
    var freeSpaceBytes = 0L

    val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v")
    val imageExts = setOf("jpg", "jpeg", "png", "webp", "heic", "raw", "svg", "gif", "bmp", "ico")
    val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid")
    val docExts = setOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub")

    val stack = ArrayDeque<Pair<CompactNode, Boolean>>()
    stack.add(rootNode to false)

    while (stack.isNotEmpty()) {
        val (node, isInsideAndroid) = stack.removeLast()
        val name = node.name

        if (name == "[Free Space]") {
            freeSpaceBytes += node.size
            continue
        }
        if (name == "[System & OS]") {
            systemBytes += node.size
            continue
        }
        if (name == "[Recycle Bin]" || name == "Recycle Bin") {
            binBytes += node.size
            continue
        }

        val isApp = node.children?.any { it.name.startsWith("App Code") } == true
        if (isApp || name.startsWith("App Code") || name.startsWith("APK (") || name == "Apps & System Packages") {
            appsBytes += node.size
            continue
        }

        if (name.startsWith(".trashed")) {
            binBytes += node.size
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
                isInsideAndroid -> systemBytes += node.size
                else -> otherBytes += node.size
            }
        } else {
            val isAndroid = isInsideAndroid || name.equals("Android", ignoreCase = true)
            val children = node.children
            if (children != null) {
                for (i in children.indices.reversed()) {
                    stack.add(children[i] to isAndroid)
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
        StorageCategorySummary("Videos", videoBytes, Color(0xFFFB923C), "movie"),
        StorageCategorySummary("Images", imageBytes, Color(0xFF34D399), "image"),
        StorageCategorySummary("Audio", audioBytes, Color(0xFFC084FC), "music_note"),
        StorageCategorySummary("Documents", docBytes, Color(0xFF38BDF8), "description"),
        StorageCategorySummary("Bin", binBytes, Color(0xFFF43F5E), "delete"),
        StorageCategorySummary("System", systemBytes, Color(0xFF94A3B8), "android"),
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
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    var expandedCategories by remember(rootNode) { mutableStateOf(setOf<String>()) }
    var expandedExtensions by remember(rootNode) { mutableStateOf(setOf<String>()) }
    val overview = remember(rootNode, totalDeviceSize) { calculateStorageOverview(rootNode, totalDeviceSize) }
    val usedPercent = if (overview.totalCapacity > 0L) (overview.usedSpace.toDouble() / overview.totalCapacity.toDouble() * 100.0) else 0.0

    val visibleStats = remember(stats) {
        stats.filter {
            val name = it.extension.lowercase().trim().removePrefix(".")
            name != "[system & os]" && name != "system & os" &&
            name != "[free space]" && name != "free space" &&
            name != "[recycle bin]" && name != "recycle bin" && name != "trashed" &&
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

    val categoryGroups = remember(visibleStats) {
        val assignedStats = mutableSetOf<ExtensionStat>()

        fun getStatsForSet(extSet: Set<String>): List<ExtensionStat> {
            val matched = visibleStats.filter { stat ->
                val ext = stat.extension.lowercase().removePrefix(".")
                ext in extSet
            }
            assignedStats.addAll(matched)
            return matched.sortedByDescending { it.totalSize }
        }

        val videos = getStatsForSet(videoExts)
        val images = getStatsForSet(imageExts)
        val audio = getStatsForSet(audioExts)
        val docs = getStatsForSet(docExts)
        val apps = getStatsForSet(appExts)
        val archives = getStatsForSet(archiveExts)
        val others = visibleStats.filter { it !in assignedStats }.sortedByDescending { it.totalSize }

        listOf(
            FileCategoryGroup("videos", "Videos", "movie", Color(0xFFFB923C), videoExts, videos, videos.sumOf { it.totalSize }, videos.sumOf { it.count }),
            FileCategoryGroup("images", "Images", "image", Color(0xFF34D399), imageExts, images, images.sumOf { it.totalSize }, images.sumOf { it.count }),
            FileCategoryGroup("audio", "Audio", "music_note", Color(0xFFC084FC), audioExts, audio, audio.sumOf { it.totalSize }, audio.sumOf { it.count }),
            FileCategoryGroup("docs", "Documents", "description", Color(0xFF38BDF8), docExts, docs, docs.sumOf { it.totalSize }, docs.sumOf { it.count }),
            FileCategoryGroup("apps", "Apps & Packages", "apps", Color(0xFF3B82F6), appExts, apps, apps.sumOf { it.totalSize }, apps.sumOf { it.count }),
            FileCategoryGroup("archives", "Archives & Disk Images", "archive", Color(0xFFF59E0B), archiveExts, archives, archives.sumOf { it.totalSize }, archives.sumOf { it.count }),
            FileCategoryGroup("others", "Other Files", "folder", Color(0xFF94A3B8), emptySet(), others, others.sumOf { it.totalSize }, others.sumOf { it.count })
        ).filter { it.totalSize > 0L || it.fileCount > 0 }
    }

    fun collectFilesForExtension(
        root: CompactNode,
        targetExt: String,
        rootPath: String,
        outList: ArrayList<Pair<CompactNode, String>>,
        maxLimit: Int = 1000
    ) {
        val cleanTarget = targetExt.lowercase().removePrefix(".")
        val stack = ArrayDeque<Pair<CompactNode, String>>()
        stack.add(root to rootPath)

        while (stack.isNotEmpty() && outList.size < maxLimit) {
            val (node, currentPath) = stack.removeLast()
            val name = node.name

            if (name == "Cache" || name == "App Cache" || name == "Data" || name == "App Data" ||
                name == "[Free Space]" || name == "[System & OS]" || name == "[Recycle Bin]") {
                continue
            }

            val isApp = node.children?.any { it.name.startsWith("App Code") } == true
            val ext = if (isApp || name.startsWith("App Code") || name.startsWith("APK (") || name.endsWith(".apk", ignoreCase = true) || name.endsWith(".obb", ignoreCase = true)) {
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

    val filesByExt = remember(rootNode, expandedExtensions) {
        expandedExtensions.associateWith { ext ->
            val list = ArrayList<Pair<CompactNode, String>>()
            collectFilesForExtension(rootNode, ext, rootNode.name, list)
            list.sortedByDescending { it.first.size }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Merged Header: Big "X of Y used" + Free space + Proportional bar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${FileUtils.formatFileSize(overview.usedSpace)} of ${FileUtils.formatFileSize(overview.totalCapacity)} used",
                    fontFamily = GoogleSansFlexFontFamily,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${FileUtils.formatFileSize(overview.freeSpace)} free",
                        fontFamily = GoogleSansFlexFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f%%", usedPercent),
                        fontFamily = GoogleSansFlexFontFamily,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Proportional Multi-Colored Segmented Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (overview.totalCapacity > 0L) {
                        overview.categories.forEach { cat ->
                            val weight = (cat.size.toFloat() / overview.totalCapacity.toFloat()).coerceAtLeast(0.005f)
                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .height(10.dp)
                                    .background(cat.color)
                            )
                        }
                        if (overview.freeSpace > 0L) {
                            val freeWeight = (overview.freeSpace.toFloat() / overview.totalCapacity.toFloat()).coerceAtLeast(0.005f)
                            Box(
                                modifier = Modifier
                                    .weight(freeWeight)
                                    .height(10.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Main Categories List with Sub-File Types
        categoryGroups.forEach { category ->
            val isCatExpanded = expandedCategories.contains(category.id)
            val catFraction = if (overview.totalCapacity > 0L) (category.totalSize.toDouble() / overview.totalCapacity.toDouble()).coerceIn(0.0, 1.0) else 0.0

            item(key = "cat_${category.id}") {
                ListItem(
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        expandedCategories = if (isCatExpanded) expandedCategories - category.id else expandedCategories + category.id
                                    }
                            ) {
                                MaterialSymbol(
                                    name = if (isCatExpanded) "expand_more" else "chevron_right",
                                    active = true,
                                    size = 22.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        expandedCategories = if (isCatExpanded) expandedCategories - category.id else expandedCategories + category.id
                                    }
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = FileUtils.formatFileSize(category.totalSize),
                                fontFamily = GoogleSansFlexFontFamily,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
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
                                    text = "${category.stats.size} types • ${category.fileCount} files",
                                    fontFamily = GoogleSansFlexFontFamily,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", catFraction * 100.0),
                                    fontFamily = GoogleSansFlexFontFamily,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { catFraction.toFloat() },
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
                    colors = ListItemDefaults.colors(
                        containerColor = if (isCatExpanded) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    modifier = Modifier.clickable {
                        expandedCategories = if (isCatExpanded) expandedCategories - category.id else expandedCategories + category.id
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            }

            // Sub File Types inside this Category
            if (isCatExpanded) {
                category.stats.forEach { stat ->
                    val statFraction = if (category.totalSize > 0L) (stat.totalSize.toDouble() / category.totalSize.toDouble()).coerceIn(0.0, 1.0) else 0.0
                    val icon = FileUtils.getExtensionIcon(stat.extension)
                    val iconColor = FileUtils.getFileTypeIconColor(stat.extension, isDark)
                    val hasFiles = stat.count > 0
                    val isStatExpanded = hasFiles && expandedExtensions.contains(stat.extension)

                    item(key = "ext_${category.id}_${stat.extension}") {
                        ListItem(
                            leadingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(28.dp))
                                    if (hasFiles) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    expandedExtensions = if (isStatExpanded) expandedExtensions - stat.extension else expandedExtensions + stat.extension
                                                }
                                        ) {
                                            MaterialSymbol(
                                                name = if (isStatExpanded) "expand_more" else "chevron_right",
                                                active = true,
                                                size = 20.dp,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                    } else {
                                        Spacer(modifier = Modifier.width(38.dp))
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (hasFiles) {
                                                    expandedExtensions = if (isStatExpanded) expandedExtensions - stat.extension else expandedExtensions + stat.extension
                                                }
                                            }
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = stat.extension,
                                            tint = iconColor,
                                            modifier = Modifier.size(24.dp)
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
                                        text = stat.extension,
                                        fontFamily = GoogleSansFlexFontFamily,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = FileUtils.formatFileSize(stat.totalSize),
                                        fontFamily = GoogleSansFlexFontFamily,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f%%", statFraction * 100.0),
                                            fontFamily = GoogleSansFlexFontFamily,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    LinearProgressIndicator(
                                        progress = { statFraction.toFloat() },
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
                                    expandedExtensions = if (isStatExpanded) expandedExtensions - stat.extension else expandedExtensions + stat.extension
                                }
                            } else Modifier
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 32.dp)
                        )
                    }

                    // Individual Files under this Extension
                    if (isStatExpanded) {
                        val files = filesByExt[stat.extension] ?: emptyList()

                        items(
                            items = files,
                            key = { "${category.id}_${stat.extension}_${it.second}" }
                        ) { (fileNode, filePath) ->
                            val fileFraction = if (stat.totalSize > 0L) (fileNode.size.toDouble() / stat.totalSize.toDouble()).coerceIn(0.0, 1.0) else 0.0
                            val fileColor = FileUtils.getNodeIconColor(fileNode, isDark)
                            val isApp = fileNode.children?.any { it.name.startsWith("App Code") } == true
                            val appPkg = if (isApp) FileUtils.extractPackageName(fileNode) else null
                            val fileIcon = FileUtils.getNodeIcon(fileNode, isApp)

                            ListItem(
                                leadingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(60.dp))
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            if (appPkg != null) {
                                                AppIconView(
                                                    packageName = appPkg,
                                                    contentDescription = fileNode.name,
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = fileIcon,
                                                    contentDescription = null,
                                                    tint = fileColor,
                                                    modifier = Modifier.size(22.dp)
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
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = FileUtils.formatFileSize(fileNode.size),
                                            fontFamily = GoogleSansFlexFontFamily,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f%%", fileFraction * 100.0),
                                            fontFamily = GoogleSansFlexFontFamily,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    onNodeClick(fileNode, filePath)
                                }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f),
                                modifier = Modifier.padding(start = 72.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
