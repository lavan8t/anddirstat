package com.kd.anddirstat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.treemap.TreemapCanvas
import com.kd.anddirstat.treemap.computeTreemapTiles
import com.kd.anddirstat.ui.components.AnimatedAppTitle
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.ui.components.DriveSelectorCompactChip
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.UnifiedDropdownMenu
import com.kd.anddirstat.util.StorageVolumeInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreemapScreen(
    rootNode: CompactNode,
    selectedNode: CompactNode?,
    selectedPath: String?,
    selectedTreeNodes: Map<CompactNode, String>,
    resetZoomKey: Int,
    isZoomed: Boolean = false,
    isDark: Boolean,
    pureBlack: Boolean,
    isLandscape: Boolean,
    detectedVolumes: List<StorageVolumeInfo>,
    activeVolume: StorageVolumeInfo?,
    showSystemOS: Boolean,
    showSystemApps: Boolean,
    showFreeSpace: Boolean,
    showHiddenFiles: Boolean,
    onZoomChanged: (Boolean) -> Unit = {},
    onResetZoom: () -> Unit,
    onNodeSelected: (CompactNode, String, Offset?) -> Unit,
    onNavigate: (String) -> Unit,
    onRescanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSelectVolume: (StorageVolumeInfo) -> Unit,
    onOpenCustomDriveDialog: () -> Unit,
    onToggleShowSystemOS: (Boolean) -> Unit,
    onToggleShowSystemApps: (Boolean) -> Unit,
    onToggleShowFreeSpace: (Boolean) -> Unit,
    onToggleShowHiddenFiles: (Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onDismissPopup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)
    var showFilterMenu by remember { mutableStateOf(false) }

    val baseBg = if (pureBlack && isDark) Color.Black else if (isDark) Color(0xFF08090E) else Color(0xFFF1F3F9)
    val displayMetrics = remember { context.resources.displayMetrics }
    val topTiles = remember(rootNode, isDark, displayMetrics.widthPixels, displayMetrics.heightPixels) {
        val w = displayMetrics.widthPixels.toFloat().coerceAtLeast(360f)
        val h = displayMetrics.heightPixels.toFloat().coerceAtLeast(640f)
        val tiles = computeTreemapTiles(rootNode, rootNode.name, w, h, isDark)
        tiles.filter { it.top <= 16f }
    }

    val topBar = @Composable {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .background(baseBg)
        ) {
            // Exact same blurred chromatic aurora as bottom spacer
            if (topTiles.isNotEmpty()) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(28.dp)
                ) {
                    for (tile in topTiles) {
                        val tileColor = if (tile.pkgName != null) {
                            AppIconCache.getDominantColor(tile.pkgName) ?: tile.baseColor
                        } else {
                            tile.baseColor
                        }
                        drawRect(
                            color = tileColor.copy(alpha = 0.75f),
                            topLeft = Offset(tile.left, 0f),
                            size = Size(maxOf(1f, tile.width), size.height)
                        )
                    }
                }
            }

            // Gradient overlay (same as bottom spacer, vertical orientation flipped for top)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                baseBg.copy(alpha = 0.65f),
                                baseBg.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Custom Treemap App Bar Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = if (isLandscape) maxOf(cutoutStart, 16.dp) else 16.dp, end = 8.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    AnimatedAppTitle(fontSize = 19.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (detectedVolumes.size > 1) {
                        DriveSelectorCompactChip(
                            detectedVolumes = detectedVolumes,
                            activeVolume = activeVolume,
                            onSelectVolume = onSelectVolume,
                            onOpenCustomDialog = onOpenCustomDriveDialog
                        )
                    }

                    // Reset zoom button in top right before 3-dot
                    AnimatedVisibility(
                        visible = isZoomed,
                        enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                slideInHorizontally(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) { it / 2 },
                        exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                               scaleOut(targetScale = 0.8f, animationSpec = tween(180)) +
                               slideOutHorizontally(animationSpec = tween(180)) { it / 2 }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onResetZoom()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            MaterialSymbol(
                                name = "refresh",
                                active = false,
                                size = 18.dp,
                                tint = if (isDark) Color.White else Color(0xFF1E293B)
                            )
                            Text(
                                text = "Zoom",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Normal,
                                color = if (isDark) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showFilterMenu = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            MaterialSymbol(
                                name = "more_vert",
                                active = true,
                                size = 20.dp,
                                tint = if (isDark) Color.White else Color(0xFF1E293B)
                            )
                        }

                        UnifiedDropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            onRescanClick = onRescanClick,
                            onSettingsClick = onSettingsClick,
                            showSystemOS = showSystemOS,
                            onToggleShowSystemOS = onToggleShowSystemOS,
                            showSystemApps = showSystemApps,
                            onToggleShowSystemApps = onToggleShowSystemApps,
                            showFreeSpace = showFreeSpace,
                            onToggleShowFreeSpace = onToggleShowFreeSpace,
                            showHiddenFiles = showHiddenFiles,
                            onToggleShowHiddenFiles = onToggleShowHiddenFiles
                        )
                    }
                }
            }
        }
    }

    val canvasContent = @Composable {
        Box(modifier = Modifier.fillMaxSize()) {
            TreemapCanvas(
                rootNode = rootNode,
                rootPath = rootNode.name,
                selectedNode = selectedNode,
                selectedNodes = selectedTreeNodes.keys,
                resetKey = resetZoomKey,
                isDark = isDark,
                pureBlack = pureBlack,
                onZoomChanged = onZoomChanged,
                isLandscape = isLandscape,
                onNodeSelected = onNodeSelected,
                onDismissPopup = onDismissPopup,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (isLandscape) 64.dp else 0.dp)
            )
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        topBar()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            canvasContent()
        }
    }
}
