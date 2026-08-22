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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.treemap.getNodeColor
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.util.FileUtils
import java.util.Locale

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

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (canGoBack) {
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
                    modifier = Modifier.clickable { onNavigateBack() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }

        itemsIndexed(
            items = children,
            key = { index, item -> "${item.name}_$index" }
        ) { _, child ->
            val fraction = if (parentSize > 0.0) (child.size.toDouble() / parentSize).coerceIn(0.0, 1.0) else 0.0
            val childColor = getNodeColor(child)
            val childPath = if (currentPath == "Device Storage") child.name else if (currentPath.endsWith("/")) "$currentPath${child.name}" else "$currentPath/${child.name}"
            val isApp = child.children?.any { it.name.startsWith("App Code") } == true
            val appPkg = if (isApp) FileUtils.extractPackageName(child) else null
            val icon = FileUtils.getNodeIcon(child, isApp)

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
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNodeClick(child, childPath) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }
    }
}
