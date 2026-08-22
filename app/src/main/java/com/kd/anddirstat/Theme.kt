package com.kd.anddirstat

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexFontFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("GRAD", 0f),
            FontVariation.width(100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("GRAD", 0f),
            FontVariation.width(100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("GRAD", 50f),
            FontVariation.width(100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("GRAD", 100f),
            FontVariation.width(100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(800),
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("GRAD", 120f),
            FontVariation.width(100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(900),
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("GRAD", 150f),
            FontVariation.width(140f)
        )
    )
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468B),
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFF7CD4FD),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF004D68),
    onSecondaryContainer = Color(0xFFC2E8FF),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF4D2700),
    tertiaryContainer = Color(0xFF6D3900),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

val Material3Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val Material3Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun AndDirStatTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Material3Shapes,
        typography = Material3Typography,
        content = content
    )
}
