package com.kd.anddirstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.treemap.getNodeColor
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.util.FileUtils
import java.util.Locale

@Composable
fun DiscoverView(
    rootNode: CompactNode,
    topFiles: List<TopFileEntry>,
    onNodeClick: (CompactNode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val appsContainer = rootNode.children?.firstOrNull { it.name == "Apps & System Packages" }
    val topApps = remember(appsContainer) {
        appsContainer?.children?.sortedByDescending { it.size }?.take(10) ?: emptyList()
    }
    val totalSize = rootNode.size

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Storage Analyzed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FileUtils.formatFileSize(totalSize),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Largest Files on Device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
            )
        }

        itemsIndexed(
            items = topFiles,
            key = { index, item -> "${item.path}_$index" }
        ) { index, entry ->
            val fraction = if (totalSize > 0L) (entry.node.size.toDouble() / totalSize.toDouble()).coerceIn(0.0, 1.0) else 0.0
            val color = getNodeColor(entry.node)

            ListItem(
                modifier = Modifier.clickable { onNodeClick(entry.node, entry.path) },
                leadingContent = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = color.copy(alpha = 0.16f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                },
                supportingContent = {
                    Column(modifier = Modifier.padding(top = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = FileUtils.formatFileSize(entry.node.size),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", fraction * 100.0),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = entry.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = {
                    Text(
                        text = entry.node.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }

        if (topApps.isNotEmpty()) {
            item {
                Text(
                    text = "Top Storage Consumer Apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
                )
            }

            itemsIndexed(
                items = topApps,
                key = { index, app -> "${app.name}_$index" }
            ) { _, app ->
                val fraction = if (totalSize > 0L) (app.size.toDouble() / totalSize.toDouble()).coerceIn(0.0, 1.0) else 0.0
                val pkgName = remember(app) { FileUtils.extractPackageName(app) }

                ListItem(
                    modifier = Modifier.clickable { onNodeClick(app, "Apps & System Packages/${app.name}") },
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AppIconView(
                                    packageName = pkgName,
                                    contentDescription = app.name,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    },
                    supportingContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = FileUtils.formatFileSize(app.size),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", fraction * 100.0),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    content = {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}
