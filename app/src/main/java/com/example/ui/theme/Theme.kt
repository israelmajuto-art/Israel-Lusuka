package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MauzoLightColorScheme = lightColorScheme(
    primary = DarkGreen,
    onPrimary = PureWhite,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = DarkGreenDeep,
    secondary = EmeraldGreen,
    onSecondary = PureWhite,
    secondaryContainer = ColorTokens.LightMint,
    onSecondaryContainer = DarkGreen,
    tertiary = DarkNavy,
    onTertiary = PureWhite,
    background = LightGrey,
    onBackground = DarkNavy,
    surface = PureWhite,
    onSurface = DarkNavy,
    surfaceVariant = ColorTokens.SurfaceSoft,
    onSurfaceVariant = SlateMuted,
    outline = LightGreyBorder,
    error = CrimsonAlert,
    onError = PureWhite,
    errorContainer = CrimsonSoft,
    onErrorContainer = CrimsonAlert
)

private val MauzoDarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    onPrimary = DarkNavy,
    primaryContainer = DarkGreen,
    onPrimaryContainer = EmeraldSoft,
    secondary = EmeraldGreen,
    onSecondary = DarkNavy,
    tertiary = EmeraldSoft,
    onTertiary = DarkNavy,
    background = DarkNavy,
    onBackground = PureWhite,
    surface = DarkNavyElevated,
    onSurface = PureWhite,
    surfaceVariant = ColorTokens.DarkSurfaceAlt,
    onSurfaceVariant = ColorTokens.DarkMuted,
    outline = ColorTokens.DarkBorder,
    error = ColorTokens.DarkCrimson,
    onError = DarkNavy,
    errorContainer = ColorTokens.DarkCrimsonContainer,
    onErrorContainer = CrimsonSoft
)

private object ColorTokens {
    val LightMint = androidx.compose.ui.graphics.Color(0xFFECFDF5)
    val SurfaceSoft = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
    val DarkSurfaceAlt = androidx.compose.ui.graphics.Color(0xFF273549)
    val DarkMuted = androidx.compose.ui.graphics.Color(0xFF94A3B8)
    val DarkBorder = androidx.compose.ui.graphics.Color(0xFF334155)
    val DarkCrimson = androidx.compose.ui.graphics.Color(0xFFF87171)
    val DarkCrimsonContainer = androidx.compose.ui.graphics.Color(0xFF7F1D1D)
}

val MauzoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MauzoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MauzoDarkColorScheme else MauzoLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MauzoTypography,
        shapes = MauzoShapes,
        content = content
    )
}
