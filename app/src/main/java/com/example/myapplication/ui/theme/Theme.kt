package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.myapplication.data.AppColorScheme
import com.example.myapplication.data.AppTheme

fun getColorSchemeForStyle(colorSchemeStyle: AppColorScheme, isDark: Boolean): ColorScheme {
    return if (isDark) {
        when (colorSchemeStyle) {
            AppColorScheme.GREEN_NATURE -> darkColorScheme(
                primary = GreenDarkPrimary,
                primaryContainer = GreenDarkContainer,
                secondary = GreenSecondary,
                onPrimaryContainer = Color.White
            )
            AppColorScheme.BLUE_OCEAN -> darkColorScheme(
                primary = BlueDarkPrimary,
                primaryContainer = BlueDarkContainer,
                secondary = BlueSecondary,
                onPrimaryContainer = Color.White
            )
            AppColorScheme.ORANGE_SUNSET -> darkColorScheme(
                primary = OrangeDarkPrimary,
                primaryContainer = OrangeDarkContainer,
                secondary = OrangeSecondary,
                onPrimaryContainer = Color.White
            )
            AppColorScheme.PURPLE_NEON -> darkColorScheme(
                primary = PurpleDarkPrimary,
                primaryContainer = PurpleDarkContainer,
                secondary = PurpleSecondary,
                onPrimaryContainer = Color.White
            )
            AppColorScheme.TEAL_CYAN -> darkColorScheme(
                primary = TealDarkPrimary,
                primaryContainer = TealDarkContainer,
                secondary = TealSecondary,
                onPrimaryContainer = Color.White
            )
        }
    } else {
        when (colorSchemeStyle) {
            AppColorScheme.GREEN_NATURE -> lightColorScheme(
                primary = GreenPrimary,
                primaryContainer = GreenPrimaryContainer,
                secondary = GreenSecondary,
                onPrimaryContainer = Color(0xFF1B5E20)
            )
            AppColorScheme.BLUE_OCEAN -> lightColorScheme(
                primary = BluePrimary,
                primaryContainer = BluePrimaryContainer,
                secondary = BlueSecondary,
                onPrimaryContainer = Color(0xFF0D47A1)
            )
            AppColorScheme.ORANGE_SUNSET -> lightColorScheme(
                primary = OrangePrimary,
                primaryContainer = OrangePrimaryContainer,
                secondary = OrangeSecondary,
                onPrimaryContainer = Color(0xFFBF360C)
            )
            AppColorScheme.PURPLE_NEON -> lightColorScheme(
                primary = PurplePrimary,
                primaryContainer = PurplePrimaryContainer,
                secondary = PurpleSecondary,
                onPrimaryContainer = Color(0xFF4A148C)
            )
            AppColorScheme.TEAL_CYAN -> lightColorScheme(
                primary = TealPrimary,
                primaryContainer = TealPrimaryContainer,
                secondary = TealSecondary,
                onPrimaryContainer = Color(0xFF004D40)
            )
        }
    }
}

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    colorSchemeStyle: AppColorScheme = AppColorScheme.GREEN_NATURE,
    content: @Composable () -> Unit
) {
    val isDark = when (appTheme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = getColorSchemeForStyle(colorSchemeStyle, isDark)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
