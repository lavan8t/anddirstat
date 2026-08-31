package com.kd.anddirstat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.R
import com.kd.anddirstat.GoogleSansFlexFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TitleLeadState { ICON, AND }

private var hasPlayedTitleLaunchAnimation = false

@OptIn(ExperimentalTextApi::class)
@Composable
fun AnimatedAppTitle(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 19.sp
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var leadState by remember {
        mutableStateOf(if (hasPlayedTitleLaunchAnimation) TitleLeadState.AND else TitleLeadState.ICON)
    }
    val fontWidth = remember { Animatable(if (hasPlayedTitleLaunchAnimation) 100f else 140f) }

    LaunchedEffect(Unit) {
        if (!hasPlayedTitleLaunchAnimation) {
            delay(500)
            leadState = TitleLeadState.AND
            fontWidth.snapTo(140f)
            fontWidth.animateTo(100f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
            hasPlayedTitleLaunchAnimation = true
        }
    }

    val currentFontWidth = fontWidth.value
    val dynamicAndFamily = remember(currentFontWidth) {
        FontFamily(
            Font(
                resId = R.font.google_sans_flex,
                weight = FontWeight.Bold,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(700),
                    FontVariation.Setting("ROND", 0f),
                    FontVariation.Setting("wdth", currentFontWidth)
                )
            )
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    leadState = TitleLeadState.ICON
                    delay(300)
                    leadState = TitleLeadState.AND
                    fontWidth.snapTo(140f)
                    fontWidth.animateTo(100f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                }
            }
    ) {
        AnimatedContent(
            targetState = leadState,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.8f))
                    .togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
            },
            label = "title_lead"
        ) { state ->
            when (state) {
                TitleLeadState.ICON -> {
                    Image(
                        painter = painterResource(R.drawable.ic_appbar),
                        contentDescription = "AndDirStat",
                        modifier = Modifier
                            .height(fontSize.value.dp)
                            .padding(end = 4.dp)
                    )
                }
                TitleLeadState.AND -> {
                    Text(
                        text = "And",
                        fontFamily = dynamicAndFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Text(
            text = "DirStat",
            fontFamily = GoogleSansFlexFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = fontSize,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
