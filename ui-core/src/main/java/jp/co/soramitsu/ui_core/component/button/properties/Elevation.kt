package jp.co.soramitsu.ui_core.component.button.properties

import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun buttonElevation(size: Dp, shadow: Boolean) = ButtonDefaults.elevation(
    defaultElevation = if (size == Size.ExtraSmall && shadow) 2.dp else 0.dp,
    pressedElevation = 0.dp,
    disabledElevation = 0.dp,
    hoveredElevation = 0.dp,
    focusedElevation = 0.dp,
)
