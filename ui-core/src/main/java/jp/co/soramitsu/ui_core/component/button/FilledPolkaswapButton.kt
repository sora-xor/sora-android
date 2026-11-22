package jp.co.soramitsu.ui_core.component.button

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.lit.Lit

/** A swap screen's one filled button: cut red glass, as every primary button is. */
@Composable
fun FilledPolkaswapButton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Lit.ControlRadius),
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = FilledButton(modifier, shape, size, Order.PRIMARY, enabled, text, leftIcon, rightIcon, maxLines, onClick)
