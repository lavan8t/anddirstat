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
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.ExtensionStat
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.util.FileUtils
import java.util.Locale

@Composable
fun FileTypesView(
    rootNode: CompactNode,
    stats: List<ExtensionStat>,
    totalDeviceSize: Long,
    onNodeClick: (CompactNode, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    var expandedExtensions by remember(rootNode) { mutableStateOf(setOf<String>()) }

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
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        visibleStats.forEach { stat ->
            val percent = if (totalDeviceSize > 0L) (stat.totalSize.toDouble() / totalDeviceSize.toDouble() * 100.0) else 0.0
            val fraction = (percent / 100.0).coerceIn(0.0, 1.0)
            val icon = FileUtils.getExtensionIcon(stat.extension)
            val iconColor = FileUtils.getFileTypeIconColor(stat.extension, isDark)
            val hasFiles = stat.count > 0
            val isExpanded = hasFiles && expandedExtensions.contains(stat.extension)

            item {
                ListItem(
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasFiles) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            expandedExtensions = if (isExpanded) expandedExtensions - stat.extension else expandedExtensions + stat.extension
                                        }
                                ) {
                                    MaterialSymbol(
                                        name = if (isExpanded) "expand_more" else "chevron_right",
                                        active = true,
                                        size = 20.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stat.extension,
                                    tint = iconColor,
                                    modifier = Modifier.size(28.dp)
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
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = FileUtils.formatFileSize(stat.totalSize),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
                                    text = if (stat.count > 0) "${stat.count} files" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", percent),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { fraction.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = iconColor,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = if (hasFiles) {
                        Modifier.clickable {
                            expandedExtensions = if (isExpanded) expandedExtensions - stat.extension else expandedExtensions + stat.extension
                        }
                    } else Modifier
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            }

            if (isExpanded) {
                val files = filesByExt[stat.extension] ?: emptyList()

                items(
                    items = files
                ) { (fileNode, filePath) ->
                    val fileFraction = if (stat.totalSize > 0L) (fileNode.size.toDouble() / stat.totalSize.toDouble()).coerceIn(0.0, 1.0) else 0.0
                    val fileColor = FileUtils.getNodeIconColor(fileNode, isDark)
                    val isApp = fileNode.children?.any { it.name.startsWith("App Code") } == true
                    val appPkg = if (isApp) FileUtils.extractPackageName(fileNode) else null
                    val fileIcon = FileUtils.getNodeIcon(fileNode, isApp)

                    ListItem(
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(36.dp))
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    if (appPkg != null) {
                                        AppIconView(
                                            packageName = appPkg,
                                            contentDescription = fileNode.name,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = fileIcon,
                                            contentDescription = null,
                                            tint = fileColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        },
                        headlineContent = {
                            Text(
                                text = fileNode.name,
                                style = MaterialTheme.typography.bodyLarge,
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", fileFraction * 100.0),
                                    style = MaterialTheme.typography.bodyMedium,
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
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 56.dp)
                    )
                }
            }
        }
    }
}
