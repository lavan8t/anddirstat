package com.kd.anddirstat.ui.screens.discover

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.SelectionActionBar
import com.kd.anddirstat.util.FileUtils

@Composable
fun BoxScope.DiscoverSelectionBar(
    selectedEntries: Set<TopFileEntry>,
    onClearSelection: () -> Unit,
    onDeleteClick: () -> Unit
) {
    AnimatedVisibility(
        visible = selectedEntries.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn() + scaleIn(initialScale = 0.92f),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        SelectionActionBar(
            selectedCount = selectedEntries.size,
            totalBytes = selectedEntries.sumOf { it.node.size },
            onClear = onClearSelection,
            onAction = onDeleteClick,
            clearTooltip = "Clear selection",
            actionTextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
        )
    }
}

@Composable
fun DiscoverDeleteConfirmDialog(
    selectedEntries: Set<TopFileEntry>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val count = selectedEntries.size
    val totalBytes = selectedEntries.sumOf { it.node.size }
    val hasApps = selectedEntries.any { FileUtils.extractPackageName(it.node, it.path, context) != null }
    val allApps = selectedEntries.all { FileUtils.extractPackageName(it.node, it.path, context) != null }
    val allAlreadyTrashed = selectedEntries.all { it.node.name.startsWith(".trashed") || it.path.contains(".trashed") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            MaterialSymbol(
                name = "delete",
                active = true,
                size = 28.dp,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = when {
                    allApps && count == 1 -> "Uninstall 1 app?"
                    allApps -> "Uninstall $count apps?"
                    hasApps -> "Delete / Uninstall $count items?"
                    allAlreadyTrashed && count == 1 -> "Delete 1 item permanently?"
                    allAlreadyTrashed -> "Delete $count items permanently?"
                    count == 1 -> "Move 1 item to Recycle Bin?"
                    else -> "Move $count items to Recycle Bin?"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = when {
                    allApps -> "$count applications will be uninstalled from device."
                    allAlreadyTrashed -> "This action is permanent and cannot be undone.\n\n${FileUtils.formatFileSize(totalBytes)} will be freed permanently"
                    else -> "Selected items will be moved to the Recycle Bin.\n\n${FileUtils.formatFileSize(totalBytes)} to be moved"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = if (allApps) "Uninstall" else if (hasApps) "Delete / Uninstall" else if (allAlreadyTrashed) "Delete" else "Move to Bin",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
