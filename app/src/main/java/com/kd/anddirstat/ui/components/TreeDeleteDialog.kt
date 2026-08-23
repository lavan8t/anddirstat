package com.kd.anddirstat.ui.components

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.FileUtils

@Composable
fun TreeDeleteDialog(
    selectedTreeNodes: Map<CompactNode, String>,
    context: Context,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    if (selectedTreeNodes.isEmpty()) return

    val count = selectedTreeNodes.size
    val totalBytes = selectedTreeNodes.keys.sumOf { it.size }
    val hasApps = selectedTreeNodes.any { (node, path) -> FileUtils.extractPackageName(node, path, context) != null }
    val allApps = selectedTreeNodes.all { (node, path) -> FileUtils.extractPackageName(node, path, context) != null }
    val allAlreadyTrashed = selectedTreeNodes.all { (node, path) -> node.name.startsWith(".trashed") || path.contains(".trashed") || path.contains("[Recycle Bin]") }

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
            TextButton(onClick = onConfirmDelete) {
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
