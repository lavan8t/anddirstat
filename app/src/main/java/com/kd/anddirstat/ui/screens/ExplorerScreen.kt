package com.kd.anddirstat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.util.FileUtils
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerView(
    currentNode: CompactNode,
    currentPath: String,
    canGoBack: Boolean,
    onNavigateBack: () -> Unit,
    onNodeClick: (CompactNode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val children = currentNode.children ?: emptyArray()
    val parentSize = currentNode.size.toDouble()
    var selectedNodes by remember(currentNode) { mutableStateOf(setOf<CompactNode>()) }
    val isDark = isSystemInDarkTheme()

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
            if (canGoBack && selectedNodes.isEmpty()) {
                item(key = "__parent_dir__") {
                    ListItem(
                        leadingContent = {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Parent",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        },
                        headlineContent = {
                            Text(
                                text = ".. Parent Directory",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.combinedClickable(
                            onClick = { onNavigateBack() },
                            onLongClick = null
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }

            itemsIndexed(
                items = children,
                key = { index, item -> "${item.name}_$index" }
            ) { _, child ->
                val fraction = if (parentSize > 0.0) (child.size.toDouble() / parentSize).coerceIn(0.0, 1.0) else 0.0
                val childColor = remember(child, isDark) { FileUtils.getNodeIconColor(child, isDark) }
                val childPath = if (currentPath == "Device Storage") child.name else if (currentPath.endsWith("/")) "$currentPath${child.name}" else "$currentPath/${child.name}"
                val isApp = child.children?.any { it.name.startsWith("App Code") } == true
                val appPkg = if (isApp) FileUtils.extractPackageName(child) else null
                val icon = FileUtils.getNodeIcon(child, isApp)
                val isSelected = selectedNodes.contains(child)

                ListItem(
                    leadingContent = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (appPkg != null) {
                                AppIconView(
                                    packageName = appPkg,
                                    contentDescription = child.name,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = childColor,
                                    modifier = Modifier.size(28.dp)
                                )
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
                                    .height(5.dp)
                                    .clip(CircleShape),
                                color = childColor,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    },
                    trailingContent = if (selectedNodes.isNotEmpty()) {
                        {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    selectedNodes = if (isSelected) selectedNodes - child else selectedNodes + child
                                }
                            )
                        }
                    } else null,
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selectedNodes.isNotEmpty()) {
                                selectedNodes = if (isSelected) selectedNodes - child else selectedNodes + child
                            } else {
                                onNodeClick(child, childPath)
                            }
                        },
                        onLongClick = {
                            selectedNodes = if (isSelected) selectedNodes - child else selectedNodes + child
                        }
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
                    TextButton(
                        onClick = {
                            selectedNodes = if (selectedNodes.size == children.size) emptySet() else children.toSet()
                        }
                    ) {
                        Text(
                            text = if (selectedNodes.size == children.size) "Deselect" else "Select All",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
