package jp.co.soramitsu.ui_core.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class BorderRadius(
    val s: Dp,
    val m: Dp,
    val ml: Dp,
    val xl: Dp
)

fun borderRadiuses(
    s: Dp = 12.dp,
    m: Dp = 16.dp,
    ml: Dp = 18.dp,
    xl: Dp = 18.dp
): BorderRadius = BorderRadius(
    s = s,
    m = m,
    ml = ml,
    xl = xl
)

internal val LocalBorderRadius = staticCompositionLocalOf { borderRadiuses() }
