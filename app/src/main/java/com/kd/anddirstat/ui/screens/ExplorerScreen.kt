package com.kd.anddirstat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.util.FileUtils
import java.util.Locale

data class ExplorerTreeRow(
    val node: CompactNode,
    val path: String,
    val depth: Int,
    val isExpanded: Boolean,
    val hasChildren: Boolean,
    val parentSize: Long
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerView(
    rootNode: CompactNode,
    onNodeClick: (CompactNode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedNodes by remember(rootNode) { mutableStateOf(setOf<CompactNode>()) }
    var selectedNodes by remember(rootNode) { mutableStateOf(setOf<CompactNode>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    fun flattenTree(
        parent: CompactNode,
        parentPath: String,
        depth: Int,
        outList: ArrayList<ExplorerTreeRow>
    ) {
        val children = parent.children ?: return
        val valid = children.filter { it.size > 0L }.sortedByDescending { it.size }
        for (child in valid) {
            val childPath = if (parentPath == "Device Storage") child.name
                else if (parentPath.endsWith("/")) "$parentPath${child.name}"
                else "$parentPath/${child.name}"
            val isApp = child.children?.any { it.name.startsWith("App Code") } == true
            val isDirWithChildren = child.isDirectory && !isApp && child.children?.any { it.size > 0L } == true
            val isExpanded = expandedNodes.contains(child)
            outList.add(ExplorerTreeRow(child, childPath, depth, isExpanded, isDirWithChildren, parent.size))
            if (isDirWithChildren && isExpanded) {
                flattenTree(child, childPath, depth + 1, outList)
            }
        }
    }

    val visibleRows = remember(rootNode, expandedNodes) {
        val list = ArrayList<ExplorerTreeRow>(128)
        flattenTree(rootNode, rootNode.name, 0, list)
        list
    }

    BackHandler(enabled = selectedNodes.isNotEmpty()) {
        selectedNodes = emptySet()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = if (selectedNodes.isNotEmpty()) 80.dp else 8.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            items(
                items = visibleRows
            ) { row ->
                val child = row.node
                val fraction = if (row.parentSize > 0L) (child.size.toDouble() / row.parentSize.toDouble()).coerceIn(0.0, 1.0) else 0.0
                val childColor = remember(child, isDark) { FileUtils.getNodeIconColor(child, isDark) }
                val isApp = child.children?.any { it.name.startsWith("App Code") } == true
                val appPkg = if (isApp) FileUtils.extractPackageName(child) else null
                val icon = FileUtils.getNodeIcon(child, isApp)
                val isSelected = selectedNodes.contains(child)

                ListItem(
                    leadingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (row.depth > 0) {
                                Spacer(modifier = Modifier.width((row.depth * 18).dp))
                            }

                            if (row.hasChildren) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            expandedNodes = if (row.isExpanded) expandedNodes - child else expandedNodes + child
                                        }
                                ) {
                                    MaterialSymbol(
                                        name = if (row.isExpanded) "expand_more" else "chevron_right",
                                        active = true,
                                        size = 20.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        selectedNodes = if (isSelected) selectedNodes - child else selectedNodes + child
                                    }
                            ) {
                                if (appPkg != null) {
                                    AppIconView(
                                        packageName = appPkg,
                                        contentDescription = child.name,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = childColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    },
                    headlineContent = {
                        Text(
                            text = if (child.isDirectory) "${child.name}/" else child.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (child.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = FileUtils.formatFileSize(child.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", fraction * 100.0),
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
                                color = childColor,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selectedNodes.isNotEmpty()) {
                                selectedNodes = if (isSelected) selectedNodes - child else selectedNodes + child
                            } else {
                                if (row.hasChildren) {
                                    expandedNodes = if (row.isExpanded) expandedNodes - child else expandedNodes + child
                                } else {
                                    onNodeClick(child, row.path)
                                }
                            }
                        },
                        onLongClick = {
                            if (row.hasChildren) {
                                onNodeClick(child, row.path)
                            } else {
                                selectedNodes = if (isSelected) selectedNodes - child else selectedNodes + child
                            }
                        }
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            }
        }

        // Floating Selection Bar
        AnimatedVisibility(
            visible = selectedNodes.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { selectedNodes = emptySet() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            MaterialSymbol(
                                name = "close",
                                active = true,
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${selectedNodes.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = FileUtils.formatFileSize(selectedNodes.sumOf { it.size }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            MaterialSymbol(
                                name = "delete",
                                active = true,
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteDialog && selectedNodes.isNotEmpty()) {
            val count = selectedNodes.size
            val totalBytes = selectedNodes.sumOf { it.size }
            val hasApps = selectedNodes.any { FileUtils.extractPackageName(it) != null }
            val allApps = selectedNodes.all { FileUtils.extractPackageName(it) != null }

            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        text = when {
                            allApps && count == 1 -> "Uninstall 1 app?"
                            allApps -> "Uninstall $count apps?"
                            hasApps -> "Delete / Uninstall $count items?"
                            count == 1 -> "Delete 1 item?"
                            else -> "Delete $count items?"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This action is permanent and cannot be undone.\n\nTotal space to free: ${FileUtils.formatFileSize(totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            val packagesToUninstall = mutableListOf<String>()
                            selectedNodes.forEach { node ->
                                val pkg = FileUtils.extractPackageName(node)
                                if (pkg != null) {
                                    packagesToUninstall.add(pkg)
                                } else {
                                    try {
                                        val f = FileUtils.resolveActualFile(node.name)
                                        if (f != null && f.exists()) f.deleteRecursively()
                                    } catch (_: Exception) {}
                                }
                            }
                            if (packagesToUninstall.isNotEmpty()) {
                                FileUtils.uninstallApps(context, packagesToUninstall)
                            }
                            selectedNodes = emptySet()
                        }
                    ) {
                        Text(
                            text = if (allApps) "Uninstall" else if (hasApps) "Delete / Uninstall" else "Delete",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
