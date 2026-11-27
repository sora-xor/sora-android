package jp.co.soramitsu.ui_core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    lightColors: CustomColors,
    darkColors: CustomColors,
    typography: CustomTypography,
    opacity: Opacity = opacities(),
    borderRadius: BorderRadius = borderRadiuses(),
    elevation: Elevation = elevation(),
    content: @Composable () -> Unit
) {
    val customColors = if (darkTheme) darkColors else lightColors
    CompositionLocalProvider(
        LocalCustomColors provides customColors,
        LocalCustomTypography provides typography,
        LocalOpacity provides opacity,
        LocalBorderRadius provides borderRadius,
        LocalElevation provides elevation
    ) {
        MaterialTheme(
            content = content
        )
    }
}

val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomColors.current

val MaterialTheme.customTypography: CustomTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomTypography.current

val MaterialTheme.opacity: Opacity
    @Composable
    @ReadOnlyComposable
    get() = LocalOpacity.current

val MaterialTheme.borderRadius: BorderRadius
    @Composable
    @ReadOnlyComposable
    get() = LocalBorderRadius.current

val MaterialTheme.elevation: Elevation
    @Composable
    @ReadOnlyComposable
    get() = LocalElevation.current
