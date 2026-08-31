package com.kd.anddirstat.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.util.StorageVolumeInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavScaffold(
    title: String,
    isLandscape: Boolean,
    onBack: () -> Unit,
    detectedVolumes: List<StorageVolumeInfo>,
    activeVolume: StorageVolumeInfo?,
    onSelectVolume: (StorageVolumeInfo) -> Unit,
    onOpenCustomDialog: () -> Unit,
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
    content: @Composable (PaddingValues) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    val layoutDirection = LocalLayoutDirection.current
    val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = if (isLandscape) cutoutStart else 0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (isLandscape) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                windowInsets = if (isLandscape) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MaterialSymbol("arrow_back", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    DriveSelectorCompactChip(
                        detectedVolumes = detectedVolumes,
                        activeVolume = activeVolume,
                        onSelectVolume = onSelectVolume,
                        onOpenCustomDialog = onOpenCustomDialog
                    )
                    Box {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showMenu = true
                            }
                        ) {
                            MaterialSymbol("more_vert", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        UnifiedDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            onRescanClick = onRescanClick,
                            onSettingsClick = onSettingsClick,
                            showSystemOS = showSystemOS,
                            onToggleShowSystemOS = onToggleShowSystemOS,
                            showSystemApps = showSystemApps,
                            onToggleShowSystemApps = onToggleShowSystemApps,
                            showFreeSpace = showFreeSpace,
                            onToggleShowFreeSpace = onToggleShowFreeSpace,
                            showHiddenFiles = showHiddenFiles,
                            onToggleShowHiddenFiles = onToggleShowHiddenFiles
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        ) {
            content(padding)
        }
    }
}
