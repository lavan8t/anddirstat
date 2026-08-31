package com.kd.anddirstat.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.util.StorageVolumeInfo

@Composable
fun UnifiedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onRescanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showSystemOS: Boolean,
    onToggleShowSystemOS: (Boolean) -> Unit,
    showSystemApps: Boolean,
    onToggleShowSystemApps: (Boolean) -> Unit,
    showFreeSpace: Boolean,
    onToggleShowFreeSpace: (Boolean) -> Unit,
    showHiddenFiles: Boolean,
    onToggleShowHiddenFiles: (Boolean) -> Unit,
    canRescan: Boolean = true
) {
    val haptic = LocalHapticFeedback.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = "Rescan",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            leadingIcon = {
                MaterialSymbol("refresh", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            onClick = {
                onDismissRequest()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRescanClick()
            },
            enabled = canRescan
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            leadingIcon = {
                MaterialSymbol("settings", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            onClick = {
                onDismissRequest()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSettingsClick()
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        DropdownMenuItem(
            text = {
                Text(
                    text = "Show OS space",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            leadingIcon = {
                MaterialSymbol(
                    name = "tune",
                    active = true,
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = showSystemOS,
                    onCheckedChange = null
                )
            },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowSystemOS(!showSystemOS)
            }
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = "Show system apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            leadingIcon = {
                MaterialSymbol(
                    name = "android",
                    active = true,
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = showSystemApps,
                    onCheckedChange = null
                )
            },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowSystemApps(!showSystemApps)
            }
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = "Show free space",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            leadingIcon = {
                MaterialSymbol(
                    name = "storage",
                    active = true,
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = showFreeSpace,
                    onCheckedChange = null
                )
            },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowFreeSpace(!showFreeSpace)
            }
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = "Show hidden files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            leadingIcon = {
                MaterialSymbol(
                    name = "visibility",
                    active = true,
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = showHiddenFiles,
                    onCheckedChange = null
                )
            },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowHiddenFiles(!showHiddenFiles)
            }
        )
    }
}

@Composable
fun DriveSelectorCompactChip(
    detectedVolumes: List<StorageVolumeInfo>,
    activeVolume: StorageVolumeInfo?,
    onSelectVolume: (StorageVolumeInfo) -> Unit,
    onOpenCustomDialog: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    if (detectedVolumes.size > 1) {
        val currentVol = activeVolume ?: detectedVolumes.firstOrNull { it.isPrimary } ?: detectedVolumes.firstOrNull()
        val volName = currentVol?.name ?: "All Drives"
        val volIcon = when {
            currentVol?.isUsb == true -> "usb"
            currentVol?.isRemovable == true -> "sd_card"
            currentVol == null -> "storage"
            else -> "smartphone"
        }

        var showDriveDropdown by remember { mutableStateOf(false) }

        Box {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDriveDropdown = true
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    MaterialSymbol(
                        name = volIcon,
                        active = true,
                        size = 15.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = volName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    MaterialSymbol(
                        name = "arrow_drop_down",
                        active = true,
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = showDriveDropdown,
                onDismissRequest = { showDriveDropdown = false }
            ) {
                Text(
                    text = "Select Drive to Map",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                detectedVolumes.forEach { vol ->
                    val isCurrent = (currentVol?.id == vol.id)
                    val icon = when {
                        vol.isUsb -> "usb"
                        vol.isRemovable -> "sd_card"
                        else -> "smartphone"
                    }
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = vol.name,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${FileUtils.formatFileSize(vol.freeBytes)} free of ${FileUtils.formatFileSize(vol.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = {
                            MaterialSymbol(
                                name = icon,
                                active = true,
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {
                            showDriveDropdown = false
                            onSelectVolume(vol)
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Custom / Multi-Drive Scan...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        MaterialSymbol(
                            name = "tune",
                            active = true,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        showDriveDropdown = false
                        onOpenCustomDialog()
                    }
                )
            }
        }
    }
}
