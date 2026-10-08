package com.kd.anddirstat.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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

private const val ActionItemCount = 2
private const val FilterItemCount = 4

@Composable
private fun FilterToggleIcon(name: String) {
    val iconColor = LocalContentColor.current
    MaterialSymbol(name = name, active = true, size = 22.dp, tint = iconColor)
}

@Composable
fun ActionDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onRescanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    canRescan: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        DropdownMenuItem(
            onClick = {
                onDismissRequest()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRescanClick()
            },
            text = {
                Text(
                    text = "Rescan",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            leadingIcon = {
                val iconColor = LocalContentColor.current
                MaterialSymbol("refresh", active = true, size = 22.dp, tint = iconColor)
            },
            enabled = canRescan
        )

        DropdownMenuItem(
            onClick = {
                onDismissRequest()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSettingsClick()
            },
            text = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            leadingIcon = {
                val iconColor = LocalContentColor.current
                MaterialSymbol("settings", active = true, size = 22.dp, tint = iconColor)
            }
        )
    }
}

@Composable
fun FilterDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    showSystemOS: Boolean,
    onToggleShowSystemOS: (Boolean) -> Unit,
    showSystemApps: Boolean,
    onToggleShowSystemApps: (Boolean) -> Unit,
    showFreeSpace: Boolean,
    onToggleShowFreeSpace: (Boolean) -> Unit,
    showHiddenFiles: Boolean,
    onToggleShowHiddenFiles: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        DropdownMenuItem(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowSystemOS(!showSystemOS)
            },
            text = {
                Text(
                    text = "Show OS space",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { FilterToggleIcon("tune") },
            trailingIcon = {
                Checkbox(
                    checked = showSystemOS,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.tertiary,
                        checkmarkColor = MaterialTheme.colorScheme.onTertiary,
                        uncheckedColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        )

        DropdownMenuItem(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowSystemApps(!showSystemApps)
            },
            text = {
                Text(
                    text = "Show system apps",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { FilterToggleIcon("android") },
            trailingIcon = {
                Checkbox(
                    checked = showSystemApps,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.tertiary,
                        checkmarkColor = MaterialTheme.colorScheme.onTertiary,
                        uncheckedColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        )

        DropdownMenuItem(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowFreeSpace(!showFreeSpace)
            },
            text = {
                Text(
                    text = "Show free space",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { FilterToggleIcon("storage") },
            trailingIcon = {
                Checkbox(
                    checked = showFreeSpace,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.tertiary,
                        checkmarkColor = MaterialTheme.colorScheme.onTertiary,
                        uncheckedColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        )

        DropdownMenuItem(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowHiddenFiles(!showHiddenFiles)
            },
            text = {
                Text(
                    text = "Show hidden files",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { FilterToggleIcon("visibility") },
            trailingIcon = {
                Checkbox(
                    checked = showHiddenFiles,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.tertiary,
                        checkmarkColor = MaterialTheme.colorScheme.onTertiary,
                        uncheckedColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        )
    }
}

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
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            onClick = {
                onDismissRequest()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRescanClick()
            },
            text = {
                Text(
                    text = "Rescan",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            leadingIcon = {
                val iconColor = LocalContentColor.current
                MaterialSymbol("refresh", active = true, size = 22.dp, tint = iconColor)
            },
            enabled = canRescan
        )

        DropdownMenuItem(
            onClick = {
                onDismissRequest()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSettingsClick()
            },
            text = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            leadingIcon = {
                val iconColor = LocalContentColor.current
                MaterialSymbol("settings", active = true, size = 22.dp, tint = iconColor)
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        DropdownMenuItem(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowSystemOS(!showSystemOS)
            },
            text = {
                Text(
                    text = "Show OS space",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { FilterToggleIcon("tune") },
            trailingIcon = {
                Checkbox(
                    checked = showSystemOS,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.tertiary,
                        checkmarkColor = MaterialTheme.colorScheme.onTertiary,
                        uncheckedColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        )

        DropdownMenuItem(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowSystemApps(!showSystemApps)
            },
            text = {
                Text(
                    text = "Show system apps",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { FilterToggleIcon("android") },
            trailingIcon = {
                Checkbox(
                    checked = showSystemApps,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.tertiary,
                        checkmarkColor = MaterialTheme.colorScheme.onTertiary,
                        uncheckedColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        )

        DropdownMenuItem(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowFreeSpace(!showFreeSpace)
            },
            text = {
                Text(
                    text = "Show free space",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { FilterToggleIcon("storage") },
            trailingIcon = {
                Checkbox(
                    checked = showFreeSpace,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.tertiary,
                        checkmarkColor = MaterialTheme.colorScheme.onTertiary,
                        uncheckedColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        )

        DropdownMenuItem(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleShowHiddenFiles(!showHiddenFiles)
            },
            text = {
                Text(
                    text = "Show hidden files",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { FilterToggleIcon("visibility") },
            trailingIcon = {
                Checkbox(
                    checked = showHiddenFiles,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.tertiary,
                        checkmarkColor = MaterialTheme.colorScheme.onTertiary,
                        uncheckedColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
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
