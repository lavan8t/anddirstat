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
import com.google.android.material.color.utilities.Scheme

enum class AppTheme(val key: String, val title: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark")
}

// Accent colors — Android Green is default, Neon palette for AMOLED
enum class AccentColor(
    val key: String,
    val label: String,
    val seed: Color
) {
    GREEN("green", "Android", Color(0xFF1DB954)),
    BLUE("blue", "Blue", Color(0xFF2196F3)),
    RED("red", "Red", Color(0xFFE53935)),
    ORANGE("orange", "Orange", Color(0xFFFF6D00)),
    TEAL("teal", "Teal", Color(0xFF009688)),
    NEON_GREEN("neon_green", "Neon Green", Color(0xFF39FF14)),
    NEON_PINK("neon_pink", "Neon Pink", Color(0xFFFF10F0)),
    NEON_YELLOW("neon_yellow", "Neon Yellow", Color(0xFFFFFF00))
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
    pureBlack: Boolean = false,
    accentColor: AccentColor = AccentColor.GREEN,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appTheme) {
        AppTheme.SYSTEM -> systemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    // Official Google material-color-utilities: generates full tonal palette from seed via HCT
    val seed = accentColor.seed.toArgb()
    val mdcScheme = if (isDark) Scheme.dark(seed) else Scheme.light(seed)
    val base = if (isDark) {
        darkColorScheme(
            primary = Color(mdcScheme.primary),
            onPrimary = Color(mdcScheme.onPrimary),
            primaryContainer = Color(mdcScheme.primaryContainer),
            onPrimaryContainer = Color(mdcScheme.onPrimaryContainer),
            secondary = Color(mdcScheme.secondary),
            onSecondary = Color(mdcScheme.onSecondary),
            secondaryContainer = Color(mdcScheme.secondaryContainer),
            onSecondaryContainer = Color(mdcScheme.onSecondaryContainer),
            tertiary = Color(mdcScheme.tertiary),
            onTertiary = Color(mdcScheme.onTertiary),
            tertiaryContainer = Color(mdcScheme.tertiaryContainer),
            onTertiaryContainer = Color(mdcScheme.onTertiaryContainer),
            error = Color(mdcScheme.error),
            onError = Color(mdcScheme.onError),
            errorContainer = Color(mdcScheme.errorContainer),
            onErrorContainer = Color(mdcScheme.onErrorContainer),
            background = Color(mdcScheme.background),
            onBackground = Color(mdcScheme.onBackground),
            surface = Color(mdcScheme.surface),
            onSurface = Color(mdcScheme.onSurface),
            surfaceVariant = Color(mdcScheme.surfaceVariant),
            onSurfaceVariant = Color(mdcScheme.onSurfaceVariant),
            outline = Color(mdcScheme.outline),
            inverseSurface = Color(mdcScheme.inverseSurface),
            inverseOnSurface = Color(mdcScheme.inverseOnSurface),
            inversePrimary = Color(mdcScheme.inversePrimary),
        )
    } else {
        lightColorScheme(
            primary = Color(mdcScheme.primary),
            onPrimary = Color(mdcScheme.onPrimary),
            primaryContainer = Color(mdcScheme.primaryContainer),
            onPrimaryContainer = Color(mdcScheme.onPrimaryContainer),
            secondary = Color(mdcScheme.secondary),
            onSecondary = Color(mdcScheme.onSecondary),
            secondaryContainer = Color(mdcScheme.secondaryContainer),
            onSecondaryContainer = Color(mdcScheme.onSecondaryContainer),
            tertiary = Color(mdcScheme.tertiary),
            onTertiary = Color(mdcScheme.onTertiary),
            tertiaryContainer = Color(mdcScheme.tertiaryContainer),
            onTertiaryContainer = Color(mdcScheme.onTertiaryContainer),
            error = Color(mdcScheme.error),
            onError = Color(mdcScheme.onError),
            errorContainer = Color(mdcScheme.errorContainer),
            onErrorContainer = Color(mdcScheme.onErrorContainer),
            background = Color(mdcScheme.background),
            onBackground = Color(mdcScheme.onBackground),
            surface = Color(mdcScheme.surface),
            onSurface = Color(mdcScheme.onSurface),
            surfaceVariant = Color(mdcScheme.surfaceVariant),
            onSurfaceVariant = Color(mdcScheme.onSurfaceVariant),
            outline = Color(mdcScheme.outline),
            inverseSurface = Color(mdcScheme.inverseSurface),
            inverseOnSurface = Color(mdcScheme.inverseOnSurface),
            inversePrimary = Color(mdcScheme.inversePrimary),
        )
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

    val animatedColorScheme = colorScheme.animated()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = animatedColorScheme.background.toArgb()
                window.navigationBarColor = animatedColorScheme.surfaceContainer.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = animatedColorScheme,
        shapes = Material3Shapes,
        typography = Material3Typography,
        content = content
    )
}

@Composable
fun ColorScheme.animated(
    animationSpec: AnimationSpec<Color> = tween(durationMillis = 250, easing = FastOutSlowInEasing)
): ColorScheme {
    return copy(
        primary = animateColorAsState(primary, animationSpec, label = "p").value,
        onPrimary = animateColorAsState(onPrimary, animationSpec, label = "op").value,
        primaryContainer = animateColorAsState(primaryContainer, animationSpec, label = "pc").value,
        onPrimaryContainer = animateColorAsState(onPrimaryContainer, animationSpec, label = "opc").value,
        secondary = animateColorAsState(secondary, animationSpec, label = "s").value,
        onSecondary = animateColorAsState(onSecondary, animationSpec, label = "os").value,
        secondaryContainer = animateColorAsState(secondaryContainer, animationSpec, label = "sc").value,
        onSecondaryContainer = animateColorAsState(onSecondaryContainer, animationSpec, label = "osc").value,
        tertiary = animateColorAsState(tertiary, animationSpec, label = "t").value,
        onTertiary = animateColorAsState(onTertiary, animationSpec, label = "ot").value,
        tertiaryContainer = animateColorAsState(tertiaryContainer, animationSpec, label = "tc").value,
        onTertiaryContainer = animateColorAsState(onTertiaryContainer, animationSpec, label = "otc").value,
        background = animateColorAsState(background, animationSpec, label = "bg").value,
        onBackground = animateColorAsState(onBackground, animationSpec, label = "obg").value,
        surface = animateColorAsState(surface, animationSpec, label = "sf").value,
        onSurface = animateColorAsState(onSurface, animationSpec, label = "osf").value,
        surfaceVariant = animateColorAsState(surfaceVariant, animationSpec, label = "sfv").value,
        onSurfaceVariant = animateColorAsState(onSurfaceVariant, animationSpec, label = "osfv").value,
        surfaceContainer = animateColorAsState(surfaceContainer, animationSpec, label = "sfc").value,
        surfaceContainerLow = animateColorAsState(surfaceContainerLow, animationSpec, label = "sfcl").value,
        surfaceContainerHigh = animateColorAsState(surfaceContainerHigh, animationSpec, label = "sfch").value,
        surfaceContainerHighest = animateColorAsState(surfaceContainerHighest, animationSpec, label = "sfchx").value,
        outline = animateColorAsState(outline, animationSpec, label = "ol").value,
        outlineVariant = animateColorAsState(outlineVariant, animationSpec, label = "olv").value
    )
}
