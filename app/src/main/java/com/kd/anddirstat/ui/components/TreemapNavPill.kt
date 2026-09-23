package com.kd.anddirstat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.model.AppDestinations
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.FileUtils

private data class NavItemData(
    val label: String,
    val icon: String,
    val destination: String
)

private val NAV_ITEMS = listOf(
    NavItemData("Treemap", "grid_view", AppDestinations.TREE),
    NavItemData("Explorer", "folder", AppDestinations.EXPLORER),
    NavItemData("Usage", "pie_chart", AppDestinations.TYPES),
    NavItemData("Discover", "search", AppDestinations.DISCOVER)
)

/**
 * Landscape Nameless Navigation Rail with AndDirStat Branded Logo Header.
 */
@Composable
fun TreemapNavRail(
    currentDestination: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedTreeNodes: Map<CompactNode, String> = emptyMap(),
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)
    val hasSelection = selectedTreeNodes.isNotEmpty() && currentDestination == AppDestinations.TREE

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(72.dp + cutoutStart),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = cutoutStart, top = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: AndDirStat Branded Logo Header + Navigation Icons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Branded Logo Mark
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(
                            name = "grid_view",
                            active = true,
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Selection Actions if Active
                AnimatedVisibility(
                    visible = hasSelection,
                    enter = expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                    exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut(spring(stiffness = Spring.StiffnessMedium))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onClearSelection()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            MaterialSymbol("close", active = true, size = 18.dp, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            text = "${selectedTreeNodes.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDeleteSelected()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            MaterialSymbol("delete", active = true, size = 18.dp, tint = MaterialTheme.colorScheme.onError)
                        }
                    }
                }

                // Nameless Navigation Items (Icon-only with Material 3 active indicator pill)
                NAV_ITEMS.forEach { item ->
                    val isSelected = currentDestination == item.destination
                    val animBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "railPillBg"
                    )
                    val animTint by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "railPillTint"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "railIconScale"
                    )

                    AppTooltip(text = item.label) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(width = 54.dp, height = 36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(animBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigate(item.destination)
                                }
                        ) {
                            Box(modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }) {
                                MaterialSymbol(
                                    name = item.icon,
                                    active = isSelected,
                                    size = 24.dp,
                                    tint = animTint
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Section: Settings Icon
            val isSettingsSelected = currentDestination == AppDestinations.SETTINGS
            val settingsBg by animateColorAsState(
                targetValue = if (isSettingsSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                label = "railSettingsBg"
            )
            val settingsTint by animateColorAsState(
                targetValue = if (isSettingsSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                label = "railSettingsTint"
            )

            AppTooltip(text = "Settings") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 54.dp, height = 36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(settingsBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigate(AppDestinations.SETTINGS)
                        }
                ) {
                    MaterialSymbol(
                        name = "settings",
                        active = isSettingsSelected,
                        size = 24.dp,
                        tint = settingsTint
                    )
                }
            }
        }
    }
}

/**
 * Portrait Navigation Pill (Bottom floating pill with spring animations).
 */
@Composable
fun TreemapNavPill(
    isLandscape: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    currentDestination: String = AppDestinations.TREE,
    selectedTreeNodes: Map<CompactNode, String> = emptyMap(),
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hasSelection = selectedTreeNodes.isNotEmpty() && currentDestination == AppDestinations.TREE

    // If on Settings screen: only show Back button + Settings icon & name
    if (currentDestination == AppDestinations.SETTINGS) {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 0.dp,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTooltip(text = "Back") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            }
                    ) {
                        MaterialSymbol(
                            name = "arrow_back",
                            active = false,
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 16.dp)
                ) {
                    MaterialSymbol(
                        name = "settings",
                        active = true,
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1
                    )
                }
            }
        }
        return
    }

    Surface(
        modifier = modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        shape = if (hasSelection) RoundedCornerShape(24.dp) else CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 0.dp,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = hasSelection,
                enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut(spring(stiffness = Spring.StiffnessMedium))
            ) {
                val totalBytes = remember(selectedTreeNodes) { selectedTreeNodes.keys.sumOf { it.size } }
                Row(
                    modifier = Modifier
                        .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onClearSelection()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            MaterialSymbol("close", active = true, size = 18.dp, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text(
                                text = "${selectedTreeNodes.size} selected",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = FileUtils.formatFileSize(totalBytes, context),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteSelected()
                            }
                            .padding(horizontal = 14.dp)
                    ) {
                        MaterialSymbol("delete", active = true, size = 18.dp, tint = MaterialTheme.colorScheme.onError)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }

            // Navigation Pill Buttons Row with pure spring specs
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NAV_ITEMS.forEach { item ->
                    val isSelected = currentDestination == item.destination
                    val animBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "pillBg"
                    )
                    val animTint by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "pillTint"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "iconScale"
                    )

                    AppTooltip(text = item.label) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .height(42.dp)
                                .clip(CircleShape)
                                .background(animBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigate(item.destination)
                                }
                                .padding(horizontal = if (isSelected) 14.dp else 10.dp)
                                .animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                        ) {
                            Box(modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }) {
                                MaterialSymbol(
                                    name = item.icon,
                                    active = isSelected,
                                    size = 24.dp,
                                    tint = animTint
                                )
                            }
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) + expandHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    expandFrom = Alignment.Start
                                ),
                                exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + shrinkHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    shrinkTowards = Alignment.Start
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = animTint,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
