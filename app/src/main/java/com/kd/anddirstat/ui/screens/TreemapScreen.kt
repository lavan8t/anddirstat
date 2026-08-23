package com.kd.anddirstat.ui.screens

import android.content.Context
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.kd.anddirstat.ui.components.AnimatedAppTitle
import com.kd.anddirstat.ui.components.DriveSelectorCompactChip
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.TreemapNavPill
import com.kd.anddirstat.ui.components.TreeSelectionBar
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
    currentScale: Float,
    isDark: Boolean,
    pureBlack: Boolean,
    isLandscape: Boolean,
    detectedVolumes: List<StorageVolumeInfo>,
    activeVolume: StorageVolumeInfo?,
    showSystemOS: Boolean,
    showSystemApps: Boolean,
    showFreeSpace: Boolean,
    showHiddenFiles: Boolean,
    onScaleChanged: (Float) -> Unit,
    onResetZoom: () -> Unit,
    onNodeSelected: (CompactNode, String) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)
    var showFilterMenu by remember { mutableStateOf(false) }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isZoomed = currentScale > 1.02f || currentScale < 0.98f

    Box(modifier = modifier.fillMaxSize()) {
        // Treemap Canvas (padded below status bar)
        TreemapCanvas(
            rootNode = rootNode,
            rootPath = rootNode.name,
            selectedNode = selectedNode,
            selectedNodes = selectedTreeNodes.keys,
            resetKey = resetZoomKey,
            isDark = isDark,
            pureBlack = pureBlack,
            onScaleChanged = onScaleChanged,
            onNodeSelected = onNodeSelected,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        )

        // Status Bar Solid Fill (ensures status bar stays clean and solid)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(surfaceColor)
        )

        // Top App Bar Overlay with Linear Fade (starts below status bar, reduced height)
        if (!isLandscape) {
            // 1. Smooth Linear Fade Backdrop
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(100.dp)
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    surfaceColor,
                                    surfaceColor.copy(alpha = 0.85f),
                                    surfaceColor.copy(alpha = 0.40f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
            )

            // 2. Interactive Top Bar Controls Row
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Left Controls: Reset Zoom & Drive Selector (Top Left Corner)
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimatedVisibility(
                        visible = isZoomed,
                        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
                        exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.8f)
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
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            MaterialSymbol(
                                name = "refresh",
                                active = false,
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Zoom",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (detectedVolumes.size > 1) {
                        DriveSelectorCompactChip(
                            detectedVolumes = detectedVolumes,
                            activeVolume = activeVolume,
                            onSelectVolume = onSelectVolume,
                            onOpenCustomDialog = onOpenCustomDriveDialog
                        )
                    }
                }

                // Center Title
                Box(modifier = Modifier.align(Alignment.Center)) {
                    AnimatedAppTitle()
                }

                // Right Actions (Top Right Corner: 3-dot overflow menu)
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showFilterMenu = true
                        }
                    ) {
                        MaterialSymbol(
                            name = "more_vert",
                            active = true,
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.onSurface
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

        // Landscape Floating Top Controls
        if (isLandscape) {
            // Top Left Corner: Reset Zoom (No bg fill)
            AnimatedVisibility(
                visible = isZoomed,
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
                exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp + cutoutStart)
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
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    MaterialSymbol(
                        name = "refresh",
                        active = false,
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Zoom",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Top Right Corner: 3-dot Menu
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showFilterMenu = true
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        MaterialSymbol("more_vert", active = true, size = 24.dp, tint = MaterialTheme.colorScheme.onSurface)
                    }
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

        // Bottom Nav Pill: hidden when zoomed in or when multi-selecting
        AnimatedVisibility(
            visible = !isZoomed && selectedTreeNodes.isEmpty(),
            enter = if (isLandscape) {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150))
            } else {
                slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150))
            },
            exit = if (isLandscape) {
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(100))
            } else {
                slideOutVertically(targetOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(100))
            },
            modifier = if (isLandscape) {
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp + cutoutStart)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            }
        ) {
            TreemapNavPill(
                isLandscape = isLandscape,
                onNavigate = onNavigate
            )
        }

        AnimatedVisibility(
            visible = selectedTreeNodes.isNotEmpty(),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh)
            ) + fadeIn(tween(120, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessHigh)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessHigh)
            ) + fadeOut(tween(100, easing = FastOutSlowInEasing)) + scaleOut(targetScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessHigh)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            TreeSelectionBar(
                selectedTreeNodes = selectedTreeNodes,
                context = context,
                onClearSelection = onClearSelection,
                onDelete = onRequestDeleteSelected
            )
        }
    }
}
