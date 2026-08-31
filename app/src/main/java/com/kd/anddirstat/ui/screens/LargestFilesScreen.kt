package com.kd.anddirstat.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargestFilesScreen(
    rootNode: CompactNode,
    topFiles: List<TopFileEntry>,
    onBack: () -> Unit,
    onNodeClick: (CompactNode, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val appsContainer = remember(rootNode) {
        rootNode.children?.firstOrNull { it.name == "Apps & System Packages" }
    }
    val appEntries = remember(appsContainer) {
        appsContainer?.children
            ?.sortedByDescending { it.size }
            ?.take(10)
            ?.map { appNode ->
                TopFileEntry(
                    node = appNode,
                    path = "Apps & System Packages/${appNode.name}"
                )
            } ?: emptyList()
    }

    var selectedFilter by remember { mutableStateOf("all") } // "all", "files", "apps"

    val allEntries = remember(topFiles, appEntries, selectedFilter) {
        val list = when (selectedFilter) {
            "files" -> topFiles
            "apps" -> appEntries
            else -> topFiles + appEntries
        }
        list.sortedByDescending { it.node.size }
    }

    val totalSize = remember(allEntries) {
        allEntries.sumOf { it.node.size }
    }

    val layoutDirection = LocalLayoutDirection.current
    val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = cutoutStart),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Largest Files & Apps",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${allEntries.size} items • ${FileUtils.formatFileSize(totalSize, context)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MaterialSymbol("arrow_back", active = true, size = 24.dp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "all",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedFilter = "all"
                    },
                    label = { Text("All (${topFiles.size + appEntries.size})") }
                )
                FilterChip(
                    selected = selectedFilter == "files",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedFilter = "files"
                    },
                    label = { Text("Files (${topFiles.size})") }
                )
                FilterChip(
                    selected = selectedFilter == "apps",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedFilter = "apps"
                    },
                    label = { Text("Apps (${appEntries.size})") }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            if (allEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No large items found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        items = allEntries,
                        key = { "${it.path}_${it.node.name}_${it.node.size}" }
                    ) { entry ->
                        val isApp = entry.path.startsWith("Apps & System Packages") ||
                                entry.node.children?.any { it.name.startsWith("App Code") } == true
                        val pkgName = remember(entry.node, isApp) {
                            if (isApp) FileUtils.extractPackageName(entry.node, entry.path, context) else null
                        }

                        ListItem(
                            modifier = Modifier
                                .animateItem()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isApp && pkgName != null) {
                                        try {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.parse("package:$pkgName")
                                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                            )
                                        } catch (_: Exception) {}
                                    } else {
                                        val file = FileUtils.resolveActualFile(entry.path, context)
                                        if (file != null && file.exists() && !entry.node.isDirectory) {
                                            FileUtils.openFile(context, file)
                                        } else {
                                            onNodeClick(entry.node, entry.path)
                                        }
                                    }
                                },
                            leadingContent = {
                                Box(
                                    modifier = Modifier.size(44.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isApp && pkgName != null) {
                                        AppIconView(
                                            packageName = pkgName,
                                            contentDescription = entry.node.name,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    } else {
                                        MediaThumbnailView(
                                            node = entry.node,
                                            path = entry.path,
                                            fallbackTint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = entry.node.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                val isSystemApp = remember(entry.node) {
                                    FileUtils.AppPackageRegistry.isSystemApp(entry.node.name) ||
                                    entry.node.name.startsWith("com.android") ||
                                    entry.node.name.startsWith("android")
                                }
                                val folderName = remember(entry.path) {
                                    val parts = entry.path.trimEnd('/').split('/')
                                    if (parts.size > 1) parts[parts.size - 2] else ""
                                }
                                val subtext = when {
                                    isApp && isSystemApp -> "System"
                                    !isApp && folderName.isNotEmpty() -> folderName
                                    else -> null
                                }
                                if (subtext != null) {
                                    Text(
                                        text = subtext,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = FileUtils.formatFileSize(entry.node.size, context),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isApp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 72.dp)
                        )
                    }
                }
            }
        }
    }
}
