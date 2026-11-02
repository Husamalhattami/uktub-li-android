package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = LuxuryGoldLight,
    onPrimary = MidnightNavy,
    primaryContainer = DeepNavy,
    onPrimaryContainer = Color.White,
    secondary = LuxuryGoldLight,
    onSecondary = MidnightNavy,
    secondaryContainer = DeepNavy,
    tertiary = EmeraldProfit,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    outline = DarkBorder,
    error = RubyExpense
)

private val LightColorScheme = lightColorScheme(
    primary = MidnightNavy,
    onPrimary = Color.White,
    primaryContainer = DeepNavy,
    onPrimaryContainer = Color.White,
    secondary = LuxuryGold,
    onSecondary = Color.White,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = LuxuryGold,
    tertiary = EmeraldProfit,
    background = SlateBackground,
    surface = SlateSurface,
    surfaceVariant = SlateBorder,
    outline = SlateBorder,
    error = RubyExpense
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
