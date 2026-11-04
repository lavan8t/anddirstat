package com.kd.anddirstat.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ------------------------------------------------------------------------------------------------
// CRITICAL ARCHITECTURE INVARIANT (DO NOT MODIFY / REWORK / SIMPLIFY):
// Handles native Android predictive back gestures and programmatic backbutton clicks.
// 1. Predictive gesture: Tracks gesture progress in real-time via `progress.snapTo(event.progress)`.
// 2. Commit on release: Smoothly completes the remaining slide to 1.0f with `progress.animateTo(1f)`
// before calling `onBackPressed()` to prevent visual jerks or screen flashes.
// 3. Parallax background: Renders `backgroundContent` behind the active screen shifted
// by `(progress - 1f) * (screenWidth / 3f)` with a subtle depth scrim.
// 4. Cancellation: Springs back to 0.0f cleanly on gesture abort.
// Never simplify, delete, or rework this implementation.
// ------------------------------------------------------------------------------------------------
@Composable
fun PredictiveSlideBackContainer(
    onBackPressed: () -> Unit,
    backgroundContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable (triggerBack: () -> Unit) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var isPredictive by remember { mutableStateOf(false) }

    val performAnimatedBack: () -> Unit = {
        coroutineScope.launch {
            isPredictive = true
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            onBackPressed()
        }
    }

    PredictiveBackHandler(enabled = enabled) { backEvent ->
        isPredictive = true
        try {
            backEvent.collect { event ->
                progress.snapTo(event.progress)
            }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            onBackPressed()
        } catch (_: Exception) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            isPredictive = false
        }
    }

    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenWidthPx = with(density) { screenWidth.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isPredictive && backgroundContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = (progress.value - 1f) * (screenWidthPx / 3f)
                    }
            ) {
                backgroundContent()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(
                                alpha = (1f - progress.value) * 0.25f
                            )
                        )
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isPredictive) {
                        translationX = progress.value * screenWidthPx
                    }
                }
        ) {
            content(performAnimatedBack)
        }
    }
}
