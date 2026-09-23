package com.kd.anddirstat.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.util.StorageVolumeInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeSelectionBottomSheet(
    detectedVolumes: List<StorageVolumeInfo>,
    selectedVolumeIds: Set<String>,
    onToggleVolume: (String) -> Unit,
    scanAppsSelected: Boolean,
    onToggleScanApps: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hasUsageAccess = FileUtils.checkUsageAccessPermission(context)
    val effectiveScanApps = scanAppsSelected && hasUsageAccess
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Select Drives to Scan",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Choose which storage drives to analyze in your interactive treemap:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Volume Drives List
            detectedVolumes.forEach { vol ->
                val isChecked = selectedVolumeIds.contains(vol.id)
                val icon = when {
                    vol.isUsb -> "usb"
                    vol.isRemovable -> "sd_card"
                    else -> "smartphone"
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isChecked && selectedVolumeIds.size == 1 && !effectiveScanApps) {
                                // Keep at least one selected
                            } else {
                                onToggleVolume(vol.id)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialSymbol(
                            name = icon,
                            active = isChecked,
                            size = 28.dp,
                            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = vol.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${FileUtils.formatFileSize(vol.freeBytes)} free of ${FileUtils.formatFileSize(vol.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isChecked && selectedVolumeIds.size == 1 && !effectiveScanApps) {
                                    // Keep at least one
                                } else {
                                    onToggleVolume(vol.id)
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Optional Apps & Packages Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (effectiveScanApps) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (hasUsageAccess) {
                            if (effectiveScanApps && selectedVolumeIds.isEmpty()) {
                                // Keep at least one
                            } else {
                                onToggleScanApps(!scanAppsSelected)
                            }
                        } else {
                            try {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbol(
                        name = "apps",
                        active = effectiveScanApps,
                        size = 28.dp,
                        tint = if (effectiveScanApps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Installed Apps & Packages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (hasUsageAccess) "Include apk sizes & app package breakdowns"
                            else "Optional • Tap to grant Usage Access",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasUsageAccess) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }

                    Checkbox(
                        checked = effectiveScanApps,
                        enabled = hasUsageAccess,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (hasUsageAccess) {
                                onToggleScanApps(checked)
                            }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Scan Action Button
            val canScan = selectedVolumeIds.isNotEmpty() || effectiveScanApps
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm()
                },
                enabled = canScan,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Scan Selected",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
