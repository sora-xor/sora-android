package jp.co.soramitsu.ui_core.theme

import androidx.compose.runtime.staticCompositionLocalOf

data class Opacity(
    val actionBgDisabled: Float,
    val actionBgPressed: Float,
    val actionFgDisabled: Float,
    val overlayPage: Float,
    val overlayImage: Float,
)

fun opacities(
    actionBgDisabled: Float = 0.04f,
    actionBgPressed: Float = 0.12f,
    actionFgDisabled: Float = 0.16f,
    overlayPage: Float = 0.24f,
    overlayImage: Float = 0.24f,
): Opacity = Opacity(
    actionBgDisabled = actionBgDisabled,
    actionBgPressed = actionBgPressed,
    actionFgDisabled = actionFgDisabled,
    overlayPage = overlayPage,
    overlayImage = overlayImage
)

internal val LocalOpacity = staticCompositionLocalOf { opacities() }
