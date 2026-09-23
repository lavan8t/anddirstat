package com.kd.anddirstat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.util.AppNotifier
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

data class StackedSnackbarItem(
    val id: Long,
    val message: String
)

@Composable
fun StackedSnackbarHost(
    modifier: Modifier = Modifier,
    maxStacks: Int = 3,
    durationMs: Long = 3500L
) {
    val items = remember { mutableStateListOf<StackedSnackbarItem>() }
    val idCounter = remember { AtomicLong(0) }

    LaunchedEffect(Unit) {
        AppNotifier.messages.collect { message ->
            val newItem = StackedSnackbarItem(id = idCounter.incrementAndGet(), message = message)
            items.clear()
            items.add(newItem)
        }
    }

    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEach { item ->
                key(item.id) {
                    LaunchedEffect(item.id) {
                        delay(durationMs)
                        items.remove(item)
                    }

                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut() + scaleOut(targetScale = 0.9f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shadowElevation = 6.dp,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { items.remove(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
