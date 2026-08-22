package com.kd.anddirstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsView(
    showFreeSpace: Boolean,
    onToggleFreeSpace: (Boolean) -> Unit,
    showSystemApps: Boolean,
    onToggleSystemApps: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 12.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        ListItem(
            headlineContent = { Text("Show System Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Text(
                    "Display pre-installed system packages and OS services in tree visualization",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            trailingContent = {
                Checkbox(
                    checked = showSystemApps,
                    onCheckedChange = onToggleSystemApps
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onToggleSystemApps(!showSystemApps) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        ListItem(
            headlineContent = { Text("Show Free Space", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Text(
                    "Display unallocated disk partition in treemap and explorer visualizations",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            trailingContent = {
                Checkbox(
                    checked = showFreeSpace,
                    onCheckedChange = onToggleFreeSpace
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onToggleFreeSpace(!showFreeSpace) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
