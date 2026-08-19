package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CleanPrimaryContainer,
    onPrimary = CleanOnPrimaryContainer,
    primaryContainer = CleanDarkContainer,
    onPrimaryContainer = CleanDarkTextPrimary,
    secondary = CleanDarkTextSecondary,
    secondaryContainer = CleanDarkContainer,
    onSecondaryContainer = CleanDarkTextPrimary,
    background = CleanDarkBackground,
    surface = CleanDarkSurface,
    surfaceVariant = CleanDarkContainer,
    outline = CleanDarkSurfaceBorder,
    outlineVariant = CleanDarkSurfaceBorder
)

private val LightColorScheme = lightColorScheme(
    primary = CleanPrimary,
    onPrimary = CleanOnPrimary,
    primaryContainer = CleanPrimaryContainer,
    onPrimaryContainer = CleanOnPrimaryContainer,
    secondary = CleanTextSecondary,
    secondaryContainer = CleanContainer,
    onSecondaryContainer = CleanTextPrimary,
    background = CleanBackground,
    surface = CleanSurface,
    surfaceVariant = CleanContainer,
    outline = CleanSurfaceBorder,
    outlineVariant = CleanSurfaceBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep Clean Minimalism crisp palette intact
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
