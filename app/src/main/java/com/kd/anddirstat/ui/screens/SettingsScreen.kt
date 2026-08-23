package com.kd.anddirstat.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.AccentColor
import com.kd.anddirstat.AppTheme
import com.kd.anddirstat.GoogleSansFlexTitleAndFamily
import com.kd.anddirstat.GoogleSansFlexTitleDirStatFamily
import com.kd.anddirstat.ui.components.MaterialSymbol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    pureBlack: Boolean,
    onTogglePureBlack: (Boolean) -> Unit,
    dynamicTheme: Boolean,
    onToggleDynamicTheme: (Boolean) -> Unit,
    accentColor: AccentColor,
    onSelectAccent: (AccentColor) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkActive = currentTheme == AppTheme.DARK || currentTheme == AppTheme.SYSTEM
    val isDynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val showAccentItem = !dynamicTheme || !isDynamicSupported

    var showThemeBottomSheet by remember { mutableStateOf(false) }
    var showAccentBottomSheet by remember { mutableStateOf(false) }

    val themeSheetState = rememberModalBottomSheetState()
    val accentSheetState = rememberModalBottomSheetState()

    // Shapes: Terminal (start/end) rounded more (24dp), intermediate rounded less (4dp)
    val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val middleShape = RoundedCornerShape(4.dp)
    val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    val singleShape = RoundedCornerShape(24.dp)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Appearance Header
        item {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }

        // 1. Theme Item (Top terminal rounded, no right arrow)
        item {
            val hasFollowingItems = isDynamicSupported || showAccentItem
            val shape = if (hasFollowingItems) topShape else singleShape

            ListItem(
                headlineContent = {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                supportingContent = {
                    val supportingText = if (isDarkActive && pureBlack) "${currentTheme.title} (Pure Black)" else currentTheme.title
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingContent = {
                    MaterialSymbol(
                        name = when (currentTheme) {
                            AppTheme.SYSTEM -> "brightness_medium"
                            AppTheme.LIGHT  -> "light_mode"
                            AppTheme.DARK   -> "dark_mode"
                        },
                        active = true,
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(shape)
                    .clickable { showThemeBottomSheet = true }
            )
        }

        // 2. Dynamic Color Toggle (Middle if accent shown, bottom if last)
        if (isDynamicSupported) {
            item {
                val shape = if (showAccentItem) middleShape else bottomShape

                ListItem(
                    headlineContent = {
                        Text(
                            text = "Dynamic color",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "Material You wallpaper palette",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        MaterialSymbol(
                            name = "palette",
                            active = dynamicTheme,
                            size = 24.dp,
                            tint = if (dynamicTheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = dynamicTheme,
                            onCheckedChange = onToggleDynamicTheme,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(shape)
                        .clickable { onToggleDynamicTheme(!dynamicTheme) }
                )
            }
        }

        // 3. Accent Color (Bottom terminal rounded)
        if (showAccentItem) {
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Accent color",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = accentColor.label,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accentColor.getActualColor(isDarkActive))
                        )
                    },
                    trailingContent = {
                        MaterialSymbol(
                            name = "chevron_right",
                            active = true,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(bottomShape)
                        .clickable { showAccentBottomSheet = true }
                )
            }
        }

        // App name & version footer
        item {
            Spacer(modifier = Modifier.height(40.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "And",
                        fontFamily = GoogleSansFlexTitleAndFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "DirStat",
                        fontFamily = GoogleSansFlexTitleDirStatFamily,
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "v1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }

    // Theme Bottom Sheet (Contains Theme Choices + Pure Black Toggle)
    if (showThemeBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeBottomSheet = false },
            sheetState = themeSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Choose theme",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                AppTheme.entries.forEach { theme ->
                    val isSelected = currentTheme == theme
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onSelectTheme(theme)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MaterialSymbol(
                                    name = when (theme) {
                                        AppTheme.SYSTEM -> "brightness_medium"
                                        AppTheme.LIGHT  -> "light_mode"
                                        AppTheme.DARK   -> "dark_mode"
                                    },
                                    active = isSelected,
                                    size = 24.dp,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.size(16.dp))
                                Text(
                                    text = theme.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectTheme(theme)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // Pure Black toggle moved inside Theme BottomSheet
                if (isDarkActive) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onTogglePureBlack(!pureBlack) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                MaterialSymbol(
                                    name = "dark_mode",
                                    active = pureBlack,
                                    size = 24.dp,
                                    tint = if (pureBlack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.size(16.dp))
                                Column {
                                    Text(
                                        text = "Pure black",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "AMOLED pitch black background",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = pureBlack,
                                onCheckedChange = onTogglePureBlack,
                                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }

    // Accent Color Bottom Sheet
    if (showAccentBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccentBottomSheet = false },
            sheetState = accentSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Choose accent color",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AccentColor.entries) { ac ->
                        val isSelected = accentColor == ac
                        val actualColor = ac.getActualColor(isDarkActive)
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.border(
                                        3.dp, MaterialTheme.colorScheme.primary, CircleShape
                                    ) else Modifier
                                )
                                .padding(if (isSelected) 4.dp else 0.dp)
                                .clip(CircleShape)
                                .background(actualColor)
                                .clickable {
                                    onSelectAccent(ac)
                                    showAccentBottomSheet = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                MaterialSymbol(
                                    name = "check",
                                    active = true,
                                    size = 22.dp,
                                    tint = if (isDarkActive) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
