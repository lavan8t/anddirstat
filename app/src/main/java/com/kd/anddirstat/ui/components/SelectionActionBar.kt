package com.kd.anddirstat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.util.FileUtils

/**
 * Shared selection controls for every selection surface in the app (discover bar,
 * nav pill, landscape rail). Differences are parameters, never layout forks.
 *
 * Set [containerSurface] to false when the host already draws a Surface and the
 * controls must read as part of that surface rather than a card sitting on it.
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    totalBytes: Long,
    onClear: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    vertical: Boolean = false,
    showActionLabel: Boolean = true,
    actionLabel: String = "Delete",
    actionIcon: String = "delete",
    clearTooltip: String? = null,
    clearButtonColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    countTextStyle: TextStyle = MaterialTheme.typography.titleMedium,
    sizeTextStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    actionTextStyle: TextStyle = MaterialTheme.typography.labelLarge,
    containerSurface: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    containerShape: Shape = RoundedCornerShape(28.dp),
    containerShadowElevation: Dp = 3.dp,
    containerTonalElevation: Dp = 2.dp
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val clearSize = if (compact) 34.dp else 42.dp
    val clearIconSize = if (compact) 18.dp else 22.dp
    val groupSpacing = if (compact) 8.dp else 12.dp
    val actionSpacing = if (compact) 4.dp else 6.dp
    val actionHeight = if (compact) 34.dp else 44.dp
    val actionIconSize = if (compact) 18.dp else 20.dp
    val actionShape = if (compact) CircleShape else RoundedCornerShape(24.dp)
    val actionPadding = if (compact) {
        PaddingValues(horizontal = 14.dp)
    } else {
        PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    }
    val controlGap = if (compact) 6.dp else 8.dp

    val clearControl: @Composable () -> Unit = {
        Surface(
            shape = CircleShape,
            color = clearButtonColor,
            modifier = Modifier.size(clearSize)
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClear()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                MaterialSymbol(
                    name = "close",
                    active = true,
                    size = clearIconSize,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    val actionButton: @Composable () -> Unit = {
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onAction()
            },
            shape = actionShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            contentPadding = if (showActionLabel) actionPadding else PaddingValues(0.dp),
            modifier = Modifier
                .height(actionHeight)
                .then(if (showActionLabel) Modifier else Modifier.size(actionHeight))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(actionSpacing)
            ) {
                MaterialSymbol(
                    name = actionIcon,
                    active = true,
                    size = actionIconSize,
                    tint = MaterialTheme.colorScheme.onError
                )
                if (showActionLabel) {
                    Text(
                        text = actionLabel,
                        style = actionTextStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    }

    val labels: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "$selectedCount selected",
                style = countTextStyle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = FileUtils.formatFileSize(totalBytes, context),
                style = sizeTextStyle,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val content: @Composable () -> Unit = {
        if (vertical) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(controlGap)
            ) {
                clearControl()
                labels()
                actionButton()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(groupSpacing)
                ) {
                    if (clearTooltip != null) {
                        AppTooltip(text = clearTooltip) { clearControl() }
                    } else {
                        clearControl()
                    }
                    labels()
                }
                actionButton()
            }
        }
    }

    if (containerSurface) {
        Surface(
            shape = containerShape,
            color = containerColor,
            shadowElevation = containerShadowElevation,
            tonalElevation = containerTonalElevation,
            modifier = modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        Box(modifier = modifier) { content() }
    }
}