package com.kd.anddirstat

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.android.material.color.utilities.MaterialDynamicColors

enum class AppTheme(val key: String, val title: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark")
}

// Accent colors — vibrant seed palettes
enum class AccentColor(
    val key: String,
    val label: String,
    val seed: Color
) {
    RED("red", "Red", Color(0xFFE53935)),
    ORANGE("orange", "Orange", Color(0xFFFF6D00)),
    NEON_YELLOW("neon_yellow", "Neon Yellow", Color(0xFFFFFF00)),
    NEON_GREEN("neon_green", "Neon Green", Color(0xFF39FF14)),
    GREEN("green", "Android", Color(0xFF1DB954)),
    TEAL("teal", "Teal", Color(0xFF009688)),
    BLUE("blue", "Blue", Color(0xFF2196F3)),
    NEON_PINK("neon_pink", "Neon Pink", Color(0xFFFF10F0));

    fun getActualColor(isDark: Boolean): Color {
        val hct = Hct.fromInt(seed.toArgb())
        val scheme = SchemeVibrant(hct, isDark, 0.0)
        return Color(MaterialDynamicColors().primary().getArgb(scheme))
    }
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

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexStraightFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.Setting("ROND", 0f),
            FontVariation.Setting("GRAD", 0f),
            FontVariation.width(100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.Setting("ROND", 0f),
            FontVariation.Setting("GRAD", 0f),
            FontVariation.width(100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.Setting("ROND", 0f),
            FontVariation.Setting("GRAD", 50f),
            FontVariation.width(100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.Setting("ROND", 0f),
            FontVariation.Setting("GRAD", 100f),
            FontVariation.width(100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(900),
            FontVariation.Setting("ROND", 0f),
            FontVariation.Setting("GRAD", 150f),
            FontVariation.width(100f)
        )
    )
)

val GoogleSansFlexStraightRegularFamily = GoogleSansFlexStraightFamily

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexTitleAndFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.Setting("ROND", 0f),
            FontVariation.Setting("GRAD", 100f),
            FontVariation.width(100f)
        )
    )
)

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexTitleDirStatFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(900),
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("GRAD", 150f),
            FontVariation.width(140f)
        )
    )
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
    displayMedium = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
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
    pureBlack: Boolean = false,
    dynamicTheme: Boolean = true,
    accentColor: AccentColor = AccentColor.GREEN,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appTheme) {
        AppTheme.SYSTEM -> systemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    val context = LocalContext.current
    val base = if (dynamicTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // Official Google material-color-utilities: generates vibrant dynamic scheme
        val seed = accentColor.seed.toArgb()
        val hct = Hct.fromInt(seed)
        val dynamicScheme = SchemeVibrant(hct, isDark, 0.0)
        val dyn = MaterialDynamicColors()

        if (isDark) {
            darkColorScheme(
                primary = Color(dyn.primary().getArgb(dynamicScheme)),
                onPrimary = Color(dyn.onPrimary().getArgb(dynamicScheme)),
                primaryContainer = Color(dyn.primaryContainer().getArgb(dynamicScheme)),
                onPrimaryContainer = Color(dyn.onPrimaryContainer().getArgb(dynamicScheme)),
                secondary = Color(dyn.secondary().getArgb(dynamicScheme)),
                onSecondary = Color(dyn.onSecondary().getArgb(dynamicScheme)),
                secondaryContainer = Color(dyn.secondaryContainer().getArgb(dynamicScheme)),
                onSecondaryContainer = Color(dyn.onSecondaryContainer().getArgb(dynamicScheme)),
                tertiary = Color(dyn.tertiary().getArgb(dynamicScheme)),
                onTertiary = Color(dyn.onTertiary().getArgb(dynamicScheme)),
                tertiaryContainer = Color(dyn.tertiaryContainer().getArgb(dynamicScheme)),
                onTertiaryContainer = Color(dyn.onTertiaryContainer().getArgb(dynamicScheme)),
                error = Color(dyn.error().getArgb(dynamicScheme)),
                onError = Color(dyn.onError().getArgb(dynamicScheme)),
                errorContainer = Color(dyn.errorContainer().getArgb(dynamicScheme)),
                onErrorContainer = Color(dyn.onErrorContainer().getArgb(dynamicScheme)),
                background = Color(dyn.background().getArgb(dynamicScheme)),
                onBackground = Color(dyn.onBackground().getArgb(dynamicScheme)),
                surface = Color(dyn.surface().getArgb(dynamicScheme)),
                onSurface = Color(dyn.onSurface().getArgb(dynamicScheme)),
                surfaceVariant = Color(dyn.surfaceVariant().getArgb(dynamicScheme)),
                onSurfaceVariant = Color(dyn.onSurfaceVariant().getArgb(dynamicScheme)),
                outline = Color(dyn.outline().getArgb(dynamicScheme)),
                outlineVariant = Color(dyn.outlineVariant().getArgb(dynamicScheme)),
                surfaceContainer = Color(dyn.surfaceContainer().getArgb(dynamicScheme)),
                surfaceContainerLow = Color(dyn.surfaceContainerLow().getArgb(dynamicScheme)),
                surfaceContainerHigh = Color(dyn.surfaceContainerHigh().getArgb(dynamicScheme)),
                surfaceContainerHighest = Color(dyn.surfaceContainerHighest().getArgb(dynamicScheme)),
                inverseSurface = Color(dyn.inverseSurface().getArgb(dynamicScheme)),
                inverseOnSurface = Color(dyn.inverseOnSurface().getArgb(dynamicScheme)),
                inversePrimary = Color(dyn.inversePrimary().getArgb(dynamicScheme)),
            )
        } else {
            lightColorScheme(
                primary = Color(dyn.primary().getArgb(dynamicScheme)),
                onPrimary = Color(dyn.onPrimary().getArgb(dynamicScheme)),
                primaryContainer = Color(dyn.primaryContainer().getArgb(dynamicScheme)),
                onPrimaryContainer = Color(dyn.onPrimaryContainer().getArgb(dynamicScheme)),
                secondary = Color(dyn.secondary().getArgb(dynamicScheme)),
                onSecondary = Color(dyn.onSecondary().getArgb(dynamicScheme)),
                secondaryContainer = Color(dyn.secondaryContainer().getArgb(dynamicScheme)),
                onSecondaryContainer = Color(dyn.onSecondaryContainer().getArgb(dynamicScheme)),
                tertiary = Color(dyn.tertiary().getArgb(dynamicScheme)),
                onTertiary = Color(dyn.onTertiary().getArgb(dynamicScheme)),
                tertiaryContainer = Color(dyn.tertiaryContainer().getArgb(dynamicScheme)),
                onTertiaryContainer = Color(dyn.onTertiaryContainer().getArgb(dynamicScheme)),
                error = Color(dyn.error().getArgb(dynamicScheme)),
                onError = Color(dyn.onError().getArgb(dynamicScheme)),
                errorContainer = Color(dyn.errorContainer().getArgb(dynamicScheme)),
                onErrorContainer = Color(dyn.onErrorContainer().getArgb(dynamicScheme)),
                background = Color(dyn.background().getArgb(dynamicScheme)),
                onBackground = Color(dyn.onBackground().getArgb(dynamicScheme)),
                surface = Color(dyn.surface().getArgb(dynamicScheme)),
                onSurface = Color(dyn.onSurface().getArgb(dynamicScheme)),
                surfaceVariant = Color(dyn.surfaceVariant().getArgb(dynamicScheme)),
                onSurfaceVariant = Color(dyn.onSurfaceVariant().getArgb(dynamicScheme)),
                outline = Color(dyn.outline().getArgb(dynamicScheme)),
                outlineVariant = Color(dyn.outlineVariant().getArgb(dynamicScheme)),
                surfaceContainer = Color(dyn.surfaceContainer().getArgb(dynamicScheme)),
                surfaceContainerLow = Color(dyn.surfaceContainerLow().getArgb(dynamicScheme)),
                surfaceContainerHigh = Color(dyn.surfaceContainerHigh().getArgb(dynamicScheme)),
                surfaceContainerHighest = Color(dyn.surfaceContainerHighest().getArgb(dynamicScheme)),
                inverseSurface = Color(dyn.inverseSurface().getArgb(dynamicScheme)),
                inverseOnSurface = Color(dyn.inverseOnSurface().getArgb(dynamicScheme)),
                inversePrimary = Color(dyn.inversePrimary().getArgb(dynamicScheme)),
            )
        }
    }

    val colorScheme = if (isDark && pureBlack) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF0A0B0E),
            surfaceContainerLow = Color(0xFF050507),
            surfaceContainerHigh = Color(0xFF121418),
            surfaceContainerHighest = Color(0xFF1A1C22),
            surfaceVariant = Color(0xFF1C1D22),
            outlineVariant = Color(0xFF2C2D35)
        )
    } else base

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
