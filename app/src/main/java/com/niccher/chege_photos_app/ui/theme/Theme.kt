package com.niccher.chege_photos_app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppTheme {
    DEFAULT, SOLARIZED, GREY, MIDNIGHT, BLACK
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val SolarizedColorScheme = lightColorScheme(
    primary = SolarizedPrimary,
    background = SolarizedBackground,
    surface = SolarizedSurface,
    onSurface = SolarizedOnSurface,
    onBackground = SolarizedOnBackground
)

private val GreyColorScheme = darkColorScheme(
    primary = GreyPrimary,
    background = GreyBackground,
    surface = GreySurface,
    onSurface = GreyOnSurface,
    onBackground = GreyOnBackground
)

private val MidnightColorScheme = darkColorScheme(
    primary = MidnightPrimary,
    background = MidnightBackground,
    surface = MidnightSurface,
    onSurface = MidnightOnSurface,
    onBackground = MidnightOnBackground
)

private val BlackColorScheme = darkColorScheme(
    primary = BlackPrimary,
    background = BlackBackground,
    surface = BlackSurface,
    onSurface = BlackOnSurface,
    onBackground = BlackOnBackground
)

@Composable
fun ChegePhotosTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.SOLARIZED -> SolarizedColorScheme
        AppTheme.GREY -> GreyColorScheme
        AppTheme.MIDNIGHT -> MidnightColorScheme
        AppTheme.BLACK -> BlackColorScheme
        AppTheme.DEFAULT -> {
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}