package com.kd.anddirstat

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class AppTheme(val key: String, val title: String, val subtitle: String) {
    SYSTEM("system", "System Default", "Follows system appearance"),
    LIGHT("light", "Light Mode", "Standard bright appearance"),
    DARK("dark", "Dark Mode", "Material 3 dark surface"),
    AMOLED("amoled", "AMOLED Black", "Pure pitch-black for OLED displays")
}

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

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF005AC1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF006684),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBEE9FF),
    onSecondaryContainer = Color(0xFF001F2A),
    tertiary = Color(0xFF8B5000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceContainer = Color(0xFFF0F0F4),
    surfaceContainerLow = Color(0xFFF6F6FA),
    surfaceContainerHigh = Color(0xFFE8E8EC),
    surfaceContainerHighest = Color(0xFFE2E2E6),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
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
    surfaceContainerLow = Color(0xFF17191E),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val AmoledColorScheme = darkColorScheme(
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
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1D22),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainer = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF050507),
    surfaceContainerHigh = Color(0xFF121418),
    surfaceContainerHighest = Color(0xFF1A1C22),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF2C2D35),
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
    appTheme: AppTheme = AppTheme.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appTheme) {
        AppTheme.SYSTEM -> systemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.AMOLED -> true
    }

    val context = LocalContext.current
    val colorScheme = when (appTheme) {
        AppTheme.AMOLED -> AmoledColorScheme
        AppTheme.LIGHT -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(context)
            } else {
                LightColorScheme
            }
        }
        AppTheme.DARK -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                DarkColorScheme
            }
        }
        AppTheme.SYSTEM -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (systemDark) DarkColorScheme else LightColorScheme
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
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
