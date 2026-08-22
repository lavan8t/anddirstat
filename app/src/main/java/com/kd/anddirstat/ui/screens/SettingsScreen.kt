package com.kd.anddirstat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.os.Build
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.AccentColor
import com.kd.anddirstat.AppTheme
import com.kd.anddirstat.GoogleSansFlexTitleAndFamily
import com.kd.anddirstat.GoogleSansFlexTitleDirStatFamily
import com.kd.anddirstat.ui.components.MaterialSymbol

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp)
    ) {
        // Appearance section header
        item {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        // Card 1: Theme Select Card
        item {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppTheme.entries.forEach { theme ->
                        val isSelected = currentTheme == theme
                        val cardBg = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        val cardFg = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .clickable { onSelectTheme(theme) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MaterialSymbol(
                                    name = when (theme) {
                                        AppTheme.SYSTEM -> "brightness_medium"
                                        AppTheme.LIGHT  -> "light_mode"
                                        AppTheme.DARK   -> "dark_mode"
                                    },
                                    active = isSelected,
                                    size = 28.dp,
                                    tint = cardFg
                                )
                                Text(
                                    text = theme.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = cardFg
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Card 2: Pure Black Toggle Card
        item {
            val textColor = if (isDarkActive) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isDarkActive) { onTogglePureBlack(!pureBlack) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Use pure black",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                    Switch(
                        checked = pureBlack && isDarkActive,
                        onCheckedChange = onTogglePureBlack,
                        enabled = isDarkActive,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // Accent color section header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Accent Color",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        // Card 1: Dynamic Theme Toggle Card
        item {
            val isDynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val textColor = if (isDynamicSupported) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isDynamicSupported) { onToggleDynamicTheme(!dynamicTheme) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dynamic color",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                        Text(
                            text = if (isDynamicSupported) "Material You wallpaper theming" else "Requires Android 12+",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDynamicSupported) 0.8f else 0.4f)
                        )
                    }
                    Switch(
                        checked = dynamicTheme && isDynamicSupported,
                        onCheckedChange = onToggleDynamicTheme,
                        enabled = isDynamicSupported,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Card 2: Custom Accent Colors Row (greyed out when dynamic theming is active)
        item {
            val isCustomActive = !dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S

            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = if (isCustomActive) 1.0f else 0.35f
                        }
                ) {
                    items(AccentColor.entries) { ac ->
                        val isSelected = isCustomActive && accentColor == ac
                        val actualColor = ac.getActualColor(isDarkActive)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape
                                    ) else Modifier
                                )
                                .padding(if (isSelected) 3.5.dp else 0.dp)
                                .clip(CircleShape)
                                .background(actualColor)
                                .clickable(enabled = isCustomActive) { onSelectAccent(ac) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                MaterialSymbol(
                                    name = "check",
                                    active = true,
                                    size = 20.dp,
                                    tint = if (isDarkActive) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // App name + version pinned at bottom center — no card, no heading
        item {
            Spacer(modifier = Modifier.height(48.dp))
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
                        fontWeight = FontWeight.Bold,
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
}
