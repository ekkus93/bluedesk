package com.augustusmachin.android_bt_kbmouse.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

// BlueDeck palette mapped to Material 3 roles. On-colors are chosen for contrast:
// dark text on the bright accents (cyan/teal), light text on the dark surfaces/indigo.
private val DarkColorScheme =
    darkColorScheme(
        primary = BlueDeckCyan,
        onPrimary = BlueDeckNavyDark,
        secondary = BlueDeckTeal,
        onSecondary = BlueDeckNavyDark,
        tertiary = BlueDeckIndigo,
        onTertiary = BlueDeckSoftWhite,
        background = BlueDeckNavyDark,
        onBackground = BlueDeckSoftWhite,
        surface = BlueDeckNavy,
        onSurface = BlueDeckSoftWhite,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = BlueDeckIndigo,
        onPrimary = BlueDeckSoftWhite,
        secondary = BlueDeckTeal,
        onSecondary = BlueDeckNavyDark,
        tertiary = BlueDeckCyan,
        onTertiary = BlueDeckNavyDark,
        background = BlueDeckSoftWhite,
        onBackground = BlueDeckNavy,
        surface = BlueDeckSurfaceLight,
        onSurface = BlueDeckNavy,
    )

@Composable
fun AndroidbtkbmouseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        SideEffect {
            // Match the system bars to the background (not the bright accent primary) so the
            // status/nav icons stay legible in both themes.
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val wic = WindowInsetsControllerCompat(window, view)
            wic.isAppearanceLightStatusBars = !darkTheme
            wic.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
