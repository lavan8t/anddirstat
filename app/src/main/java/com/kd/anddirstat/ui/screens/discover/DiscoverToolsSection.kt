package com.kd.anddirstat.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.AppDestinations
import com.kd.anddirstat.ui.components.MaterialSymbol

fun LazyListScope.discoverToolsSection(
    onNavigateTo: ((String) -> Unit)?
) {
    item(key = "tools_header") {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Tools",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    item(key = "utility_tools_grid") {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            val isWide = maxWidth >= 600.dp
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiscoverToolCard(
                        title = "Screenshots Cleaner",
                        icon = "screenshot_monitor",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).height(108.dp),
                        onClick = { onNavigateTo?.invoke(AppDestinations.CLEANER_SCREENSHOTS) }
                    )
                    DiscoverToolCard(
                        title = "Empty Folders",
                        icon = "folder_open",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f).height(108.dp),
                        onClick = { onNavigateTo?.invoke(AppDestinations.CLEANER_EMPTY_FOLDERS) }
                    )
                    DiscoverToolCard(
                        title = "Duplicates",
                        icon = "content_copy",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f).height(108.dp),
                        onClick = { onNavigateTo?.invoke(AppDestinations.CLEANER_DUPLICATES) }
                    )
                }
            } else {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DiscoverToolCard(
                            title = "Screenshots Cleaner",
                            icon = "screenshot_monitor",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).height(108.dp),
                            onClick = { onNavigateTo?.invoke(AppDestinations.CLEANER_SCREENSHOTS) }
                        )
                        DiscoverToolCard(
                            title = "Empty Folders",
                            icon = "folder_open",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f).height(108.dp),
                            onClick = { onNavigateTo?.invoke(AppDestinations.CLEANER_EMPTY_FOLDERS) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        onClick = {
                            onNavigateTo?.invoke(AppDestinations.CLEANER_DUPLICATES)
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            MaterialSymbol(
                                name = "content_copy",
                                active = true,
                                size = 28.dp,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Duplicates Cleaner",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Find and remove duplicate files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            MaterialSymbol(
                                name = "chevron_right",
                                active = true,
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DiscoverToolCard(
    title: String,
    icon: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            MaterialSymbol(
                name = icon,
                active = true,
                size = 28.dp,
                tint = tint
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
