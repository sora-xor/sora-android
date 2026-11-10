package jp.co.soramitsu.ui_core.extensions

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import jp.co.soramitsu.ui_core.theme.opacity

@Composable
fun Color.withOpacity(opacity: Float): Color {
    return this.copy(alpha = opacity)
}

@Composable
fun Color.ripple(opacity: Float = MaterialTheme.opacity.actionBgPressed): Color {
    return this.copy(alpha = opacity)
}
