package com.kd.anddirstat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.components.ActionDropdownMenu
import com.kd.anddirstat.ui.components.DriveSelectorCompactChip
import com.kd.anddirstat.ui.components.FilterDropdownMenu
import com.kd.anddirstat.ui.components.MaterialSymbol
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
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)
    var showFilterMenu by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }

    // Floating Material 3 Expressive top app bar
    val topBar = @Composable {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isLandscape) maxOf(cutoutStart, 16.dp) else 16.dp, vertical = 8.dp)
                .statusBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (detectedVolumes.size > 1) {
                    DriveSelectorCompactChip(
                        detectedVolumes = detectedVolumes,
                        activeVolume = activeVolume,
                        onSelectVolume = onSelectVolume,
                        onOpenCustomDialog = onOpenCustomDriveDialog
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    // Reset zoom button with Expressive motion physics
                    AnimatedVisibility(
                        visible = isZoomed,
                        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) +
                                slideInHorizontally(animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it / 2 },
                        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                               scaleOut(targetScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                               slideOutHorizontally(animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it / 2 }
                    ) {
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onResetZoom()
                            },
                            label = {
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                MaterialSymbol(
                                    name = "refresh",
                                    active = false,
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = CircleShape
                        )
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
                                name = "filter_list",
                                active = true,
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        FilterDropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
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

                    Box {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showActionMenu = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            MaterialSymbol(
                                name = "more_vert",
                                active = true,
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        ActionDropdownMenu(
                            expanded = showActionMenu,
                            onDismissRequest = { showActionMenu = false },
                            onRescanClick = onRescanClick,
                            onSettingsClick = onSettingsClick
                        )
                    }
                }
            }
        }
    }

    // Transparent container allowing the persistent background TreemapCanvas in MainActivity to shine through
    Box(modifier = modifier.fillMaxSize()) {
        topBar()
    }
}
