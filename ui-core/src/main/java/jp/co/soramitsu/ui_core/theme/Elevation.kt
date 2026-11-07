package jp.co.soramitsu.ui_core.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Elevation(
    val s: Dp,
    val l: Dp
)

fun elevation(
    s: Dp = 8.dp,
    l: Dp = 32.dp
): Elevation = Elevation(
    s = s,
    l = l
)

internal val LocalElevation = staticCompositionLocalOf { elevation() }
