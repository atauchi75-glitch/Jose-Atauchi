package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightNaturalistColors = lightColorScheme(
    primary = ForestDeep,
    onPrimary = Color.White,
    primaryContainer = MintSoft,
    onPrimaryContainer = ForestDeep,
    secondary = TerracottaFauna,
    onSecondary = Color.White,
    secondaryContainer = TerracottaSoft,
    onSecondaryContainer = Color(0xFF4A1E0B),
    tertiary = SlateTealFauna,
    onTertiary = Color.White,
    tertiaryContainer = SlateTealSoft,
    onTertiaryContainer = SlateTealFauna,
    background = ParchmentBg,
    onBackground = InkDark,
    surface = CardPaper,
    onSurface = InkDark,
    surfaceVariant = LinenSurface,
    onSurfaceVariant = InkMuted,
    outline = HairlineWarm,
    outlineVariant = Color(0xFFE7E2D6)
)

private val DarkNaturalistColors = darkColorScheme(
    primary = NightMintPrimary,
    onPrimary = Color(0xFF0A2619),
    primaryContainer = NightForestContainer,
    onPrimaryContainer = MintSoft,
    secondary = NightTerracotta,
    onSecondary = Color(0xFF3E1808),
    secondaryContainer = Color(0xFF4D2312),
    onSecondaryContainer = TerracottaSoft,
    tertiary = Color(0xFF93C4D6),
    onTertiary = Color(0xFF0C2A35),
    tertiaryContainer = SlateTealFauna,
    onTertiaryContainer = SlateTealSoft,
    background = NightForestBg,
    onBackground = NightTextMain,
    surface = NightForestSurface,
    onSurface = NightTextMain,
    surfaceVariant = NightForestContainer,
    onSurfaceVariant = NightTextMuted,
    outline = Color(0xFF2D483B),
    outlineVariant = Color(0xFF22372D)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FaunaGbifTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkNaturalistColors
        else -> LightNaturalistColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
