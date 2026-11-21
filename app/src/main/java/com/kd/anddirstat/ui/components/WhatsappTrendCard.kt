package com.kd.anddirstat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.util.WhatsappDayPoint
import com.kd.anddirstat.util.WhatsappMediaKind

/** Daily WhatsApp figures plus a selective old-data cleanup section, on the base surface. */
@Composable
fun WhatsappTrendCard(
    history: List<WhatsappDayPoint>,
    candidateCount: Int,
    candidateBytes: Long,
    keepKinds: Set<WhatsappMediaKind>,
    isDeleting: Boolean,
    onToggleKeep: (WhatsappMediaKind) -> Unit,
    onDeleteOld: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty() && candidateCount == 0) return
    val today = history.lastOrNull()
    val previous = history.getOrElse(history.size - 2) { today }
    val growth = if (today != null && previous != null) today.totalBytes - previous.totalBytes else 0L
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(name = "chat", active = true, size = 24.dp, tint = primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "WhatsApp Analyzer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (today != null) {
            Spacer(modifier = Modifier.heightIn(min = 8.dp))
            Text(
                text = "${FileUtils.formatFileSize(today.totalBytes)} WhatsApp media",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${FileUtils.formatFileSize(today.addedBytes)} added today across ${today.addedFiles} files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (history.size >= 2) {
                Text(
                    text = "${if (growth >= 0) "+" else ""}${FileUtils.formatFileSize(kotlin.math.abs(growth))} since ${previous!!.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.heightIn(min = 12.dp))
        Text(
            text = "Keep old media (over 1 year)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth()) {
            WhatsappMediaKind.entries.forEach { kind ->
                FilterChip(
                    selected = keepKinds.contains(kind),
                    onClick = { onToggleKeep(kind) },
                    label = { Text(kind.label) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(end = 8.dp)
                )
            }
        }

        if (candidateCount > 0) {
            Spacer(modifier = Modifier.heightIn(min = 8.dp))
            Text(
                text = "$candidateCount old files · ${FileUtils.formatFileSize(candidateBytes)} removable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.heightIn(min = 4.dp))
            Button(
                onClick = onDeleteOld,
                enabled = !isDeleting,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(if (isDeleting) "Deleting…" else "Delete old WhatsApp media")
            }
        }
    }
}
