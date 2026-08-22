package com.kd.anddirstat.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.R

@OptIn(ExperimentalTextApi::class)
private fun createSymbolsFamily(filled: Boolean) = FontFamily(
    Font(
        resId = R.font.material_symbols_rounded,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.Setting("GRAD", 200f),
            FontVariation.Setting("opsz", 20f),
            FontVariation.Setting("FILL", if (filled) 1f else 0f)
        )
    )
)

val MaterialSymbolsFilledFamily = createSymbolsFamily(true)
val MaterialSymbolsOutlinedFamily = createSymbolsFamily(false)

@Composable
fun MaterialSymbol(
    name: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = name,
        style = TextStyle(
            fontFamily = if (active) MaterialSymbolsFilledFamily else MaterialSymbolsOutlinedFamily,
            fontSize = size.value.sp,
            lineHeight = size.value.sp,
            color = tint,
            textAlign = TextAlign.Center
        ),
        modifier = modifier
    )
}
