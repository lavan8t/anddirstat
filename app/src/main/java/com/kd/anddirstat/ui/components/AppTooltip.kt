package com.kd.anddirstat.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                shape = RoundedCornerShape(8.dp),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        content()
    }
}
