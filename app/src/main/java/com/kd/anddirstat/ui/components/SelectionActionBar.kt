package com.kd.anddirstat.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.util.FileUtils

@Composable
fun SelectionActionBar(
    selectedCount: Int,
    totalBytes: Long,
    onClear: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    actionLabel: String = "Delete",
    actionIcon: String = "delete",
    clearTooltip: String? = null,
    clearButtonColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    countTextStyle: TextStyle = MaterialTheme.typography.titleMedium,
    sizeTextStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    actionTextStyle: TextStyle = MaterialTheme.typography.labelLarge
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

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 3.dp,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
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
                Column {
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
                contentPadding = actionPadding,
                modifier = Modifier.height(actionHeight)
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
}