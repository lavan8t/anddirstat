package com.kd.anddirstat.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.util.StorageChangeItem
import com.kd.anddirstat.util.StorageDayPoint

@Composable
fun StorageTrendCard(
    history: List<StorageDayPoint>,
    recentChanges: List<StorageChangeItem>,
    lifetimeFreed: Long,
    onItemClick: (CompactNode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    var selectedIndex by remember(history) { mutableIntStateOf(history.lastIndex) }
    val selectedPoint = history.getOrElse(selectedIndex) { history.last() }

    val startUsed = history.first().usedBytes
    val endUsed = history.last().usedBytes
    val weekDelta = endUsed - startUsed

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "trendAnim"
    )

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Header: Title + Net 7-day Change Pill (No Card wrapper)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol(
                    name = "insights",
                    active = true,
                    size = 22.dp,
                    tint = primaryColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Storage Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = CircleShape,
                color = if (weekDelta > 0L) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
            ) {
                val deltaText = if (weekDelta > 0L) {
                    "+${FileUtils.formatFileSize(weekDelta)} this week"
                } else if (weekDelta < 0L) {
                    "-${FileUtils.formatFileSize(-weekDelta)} this week"
                } else {
                    "Stable this week"
                }
                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (weekDelta > 0L) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Subheader: Selected / Latest Used Storage + Lifetime Freed Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = FileUtils.formatFileSize(selectedPoint.usedBytes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${selectedPoint.dayLabel} (${selectedPoint.dateKey})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (lifetimeFreed > 0L) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        MaterialSymbol(
                            name = "auto_delete",
                            active = true,
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${FileUtils.formatFileSize(lifetimeFreed)} freed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Trend Graph Canvas
        val pointCount = history.size
        val minVal = (history.minOf { it.usedBytes } * 0.985).toLong()
        val maxVal = (history.maxOf { it.usedBytes } * 1.015).toLong()
        val range = (maxVal - minVal).coerceAtLeast(1024L)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .pointerInput(history) {
                        detectTapGestures { offset ->
                            val stepX = (size.width - 32.dp.toPx()) / (pointCount - 1).coerceAtLeast(1)
                            val padX = 16.dp.toPx()
                            val clickedIdx = ((offset.x - padX + stepX / 2f) / stepX)
                                .toInt()
                                .coerceIn(0, history.lastIndex)
                            if (clickedIdx != selectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedIndex = clickedIdx
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val padStart = 16.dp.toPx()
                val padEnd = 16.dp.toPx()
                val padTop = 12.dp.toPx()
                val padBottom = 16.dp.toPx()
                val usableW = w - padStart - padEnd
                val usableH = h - padTop - padBottom
                val stepX = usableW / (pointCount - 1).coerceAtLeast(1)

                val coords = history.mapIndexed { idx, pt ->
                    val x = padStart + idx * stepX
                    val frac = ((pt.usedBytes - minVal).toFloat() / range.toFloat()).coerceIn(0f, 1f)
                    val targetY = (h - padBottom) - (frac * usableH)
                    val animY = (h - padBottom) - ((h - padBottom - targetY) * animProgress)
                    Offset(x, animY)
                }

                if (coords.size >= 2) {
                    val strokePath = Path().apply {
                        moveTo(coords[0].x, coords[0].y)
                        for (i in 0 until coords.lastIndex) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            val cx1 = (p0.x + p1.x) / 2f
                            val cy1 = p0.y
                            val cx2 = (p0.x + p1.x) / 2f
                            val cy2 = p1.y
                            cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(strokePath)
                        lineTo(coords.last().x, h - padBottom)
                        lineTo(coords.first().x, h - padBottom)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.28f * animProgress), Color.Transparent),
                            startY = padTop,
                            endY = h - padBottom
                        )
                    )

                    drawPath(
                        path = strokePath,
                        color = primaryColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                coords.forEachIndexed { i, pt ->
                    val isSelected = i == selectedIndex
                    if (isSelected) {
                        drawCircle(
                            color = primaryContainer,
                            radius = 9.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = 4.5.dp.toPx(),
                            center = pt
                        )
                    } else {
                        drawCircle(
                            color = outlineVariant,
                            radius = 2.5.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }

        // X-Axis Day Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            history.forEachIndexed { idx, pt ->
                val isSelected = idx == selectedIndex
                Text(
                    text = pt.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // What got added on what day (clean, no card, no icon background fill, no section title text)
        if (recentChanges.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(6.dp))

            recentChanges.forEach { change ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onItemClick(change.node, change.fullPath)
                        }
                        .padding(vertical = 8.dp, horizontal = 2.dp)
                ) {
                    // Filled icons as requested
                    MaterialSymbol(
                        name = if (change.node.isDirectory) "folder_fill" else "draft",
                        active = true,
                        size = 22.dp,
                        tint = if (change.node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = change.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${change.dayLabel} • ${change.parentFolder}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "+${FileUtils.formatFileSize(change.size)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }
            }
        }
    }
}
