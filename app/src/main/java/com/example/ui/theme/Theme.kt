package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.ThemeMode

private val OpenFeedDarkColorScheme = darkColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = OnCyanDark,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = OnCyanContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = OnAmberDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = EmeraldStatusDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceElevated,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.6f),
    error = RoseErrorDark
)

private val OpenFeedLightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = OnCyanLight,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = OnCyanContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = OnAmberLight,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = EmeraldStatusLight,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceElevated,
    surfaceContainer = LightSurfaceElevated,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHigh,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.7f),
    error = RoseErrorLight
)

val OpenFeedShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun resolveIsDarkTheme(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
}

@Composable
private fun animatedColor(target: Color): Color {
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 250),
        label = "themeColor"
    )
    return color
}

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    return target.copy(
        primary = animatedColor(target.primary),
        onPrimary = animatedColor(target.onPrimary),
        primaryContainer = animatedColor(target.primaryContainer),
        onPrimaryContainer = animatedColor(target.onPrimaryContainer),
        secondary = animatedColor(target.secondary),
        onSecondary = animatedColor(target.onSecondary),
        secondaryContainer = animatedColor(target.secondaryContainer),
        onSecondaryContainer = animatedColor(target.onSecondaryContainer),
        tertiary = animatedColor(target.tertiary),
        background = animatedColor(target.background),
        onBackground = animatedColor(target.onBackground),
        surface = animatedColor(target.surface),
        onSurface = animatedColor(target.onSurface),
        surfaceVariant = animatedColor(target.surfaceVariant),
        onSurfaceVariant = animatedColor(target.onSurfaceVariant),
        surfaceContainerLowest = animatedColor(target.surfaceContainerLowest),
        surfaceContainerLow = animatedColor(target.surfaceContainerLow),
        surfaceContainer = animatedColor(target.surfaceContainer),
        surfaceContainerHigh = animatedColor(target.surfaceContainerHigh),
        surfaceContainerHighest = animatedColor(target.surfaceContainerHighest),
        outline = animatedColor(target.outline),
        outlineVariant = animatedColor(target.outlineVariant),
        error = animatedColor(target.error)
    )
}

@Composable
fun OpenFeedTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = resolveIsDarkTheme(themeMode)

    val targetColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> OpenFeedDarkColorScheme
        else -> OpenFeedLightColorScheme
    }

    val colorScheme = animateColorScheme(targetColorScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OpenFeedTypography,
        shapes = OpenFeedShapes,
        content = content
    )
}
