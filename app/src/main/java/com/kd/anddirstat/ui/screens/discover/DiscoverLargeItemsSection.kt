package com.kd.anddirstat.ui.screens.discover

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.kd.anddirstat.model.AppDestinations
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

fun LazyListScope.discoverLargeItemsSection(
    displayedTop5: List<TopFileEntry>,
    selectedEntries: Set<TopFileEntry>,
    onToggleSelect: (TopFileEntry) -> Unit,
    onNodeClick: (CompactNode, String) -> Unit,
    onNodesDeleted: (Set<CompactNode>) -> Unit,
    onRefresh: () -> Unit,
    onNavigateTo: ((String) -> Unit)?
) {
    if (displayedTop5.isEmpty()) return

    item(key = "large_items_header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Large Items",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }

    itemsIndexed(
        items = displayedTop5,
        key = { index, entry -> "top5_${entry.path}_${entry.node.name}_$index" }
    ) { index, entry ->
        DiscoverLargeItemRow(
            index = index,
            totalCount = displayedTop5.size,
            entry = entry,
            isSelected = selectedEntries.contains(entry),
            isSelectionMode = selectedEntries.isNotEmpty(),
            onToggleSelect = { onToggleSelect(entry) },
            onNodeClick = onNodeClick,
            onNodesDeleted = onNodesDeleted,
            onRefresh = onRefresh
        )
        if (index < displayedTop5.lastIndex) {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }

    item(key = "large_items_more_button") {
        val haptic = LocalHapticFeedback.current
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateTo?.invoke(AppDestinations.LARGEST_FILES)
                }
            ) {
                Text(
                    text = "More",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DiscoverLargeItemRow(
    index: Int,
    totalCount: Int,
    entry: TopFileEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onNodeClick: (CompactNode, String) -> Unit,
    onNodesDeleted: (Set<CompactNode>) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val pkgName = remember(entry.node, entry.path) {
        FileUtils.extractPackageName(entry.node, entry.path, context)
            ?: FileUtils.AppPackageRegistry.getPackageName(entry.node.name)
    }
    val isApp = pkgName != null ||
            entry.path.startsWith("Apps & System Packages") ||
            entry.node.children?.any { it.name.startsWith("App Code") } == true

    val shape = when {
        totalCount == 1 -> RoundedCornerShape(20.dp)
        index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        index == totalCount - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        else -> RoundedCornerShape(4.dp)
    }

    val density = LocalDensity.current
    val revealWidth = 58.dp
    val revealWidthPx = with(density) { revealWidth.toPx() }
    val offsetX = remember(entry) { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
    ) {
        // Revealed Action Button beside the card
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(revealWidth)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { offsetX.animateTo(0f) }
                        if (isApp) {
                            val targetPkg = pkgName ?: entry.node.name
                            FileUtils.uninstallApp(context, targetPkg)
                        } else {
                            if (FavoritesManager.isStarred(context, entry.path)) {
                                AppNotifier.notify("Cannot delete starred file. Unstar manually first.")
                            } else {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val f = FileUtils.resolveActualFile(entry.path) ?: FileUtils.resolveActualFile(entry.node.name)
                                            if (f != null && f.exists()) {
                                                FileUtils.deleteOrTrashFile(f, context)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    onNodesDeleted(setOf(entry.node))
                                    onRefresh()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbol(
                    name = if (isApp) "delete_forever" else "delete",
                    active = true,
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Foreground Card
        Surface(
            shape = shape,
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(entry) {
                    var lastHapticZone = 0
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            lastHapticZone = 0
                            scope.launch {
                                val curr = offsetX.value
                                if (curr <= -revealWidthPx * 0.4f) {
                                    offsetX.animateTo(-revealWidthPx, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                } else {
                                    offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val nextX = (offsetX.value + dragAmount).coerceIn(-revealWidthPx * 1.35f, 0f)
                            val zone = if (-nextX >= revealWidthPx) 1 else 0
                            if (zone != lastHapticZone) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticZone = zone
                            }
                            scope.launch { offsetX.snapTo(nextX) }
                        }
                    )
                }
                .clickable {
                    if (offsetX.value < -10f) {
                        scope.launch { offsetX.animateTo(0f) }
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isSelectionMode) {
                            onToggleSelect()
                        } else if (isApp && pkgName != null) {
                            try {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        "package:$pkgName".toUri()
                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                )
                            } catch (_: Exception) {}
                        } else {
                            onNodeClick(entry.node, entry.path)
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isApp && pkgName != null) {
                        AppIconView(
                            packageName = pkgName,
                            contentDescription = entry.node.name,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        val iconColor = when {
                            entry.node.name.endsWith(".apk", true) -> MaterialTheme.colorScheme.error
                            entry.node.name.endsWith(".zip", true) || entry.node.name.endsWith(".iso", true) -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                        MediaThumbnailView(
                            node = entry.node,
                            path = entry.path,
                            fallbackTint = iconColor,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = entry.node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = FileUtils.formatFileSize(entry.node.size, context),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isApp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
