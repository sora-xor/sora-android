package jp.co.soramitsu.ui_core.component.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.lit.LocalLitPalette
import jp.co.soramitsu.ui_core.lit.Lit

/**
 * The primary filled button is a slab of cut red glass with its label inlaid in white: the one loud thing on a
 * screen. The secondary and tertiary ones are soft raised pads with an ink label.
 */
@Composable
fun FilledButton(
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
) {
    val colors = ButtonColors.filledButtonColors(order)
    val lit = LocalLitPalette.current
    val face = LitButtonFace(
        glass = lit.red.takeIf { order == Order.PRIMARY },
        content = if (order == Order.PRIMARY) lit.inlay else colors.backgroundColor(true).value,
        disabledFill = colors.backgroundColor(false).value,
        disabledContent = colors.contentColor(false).value,
    )
    LitButton(modifier, shape, size, face, enabled, text, leftIcon, rightIcon, maxLines, onClick)
}

@Preview
@Composable
private fun PreviewFilledButton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color.White)
            .padding(horizontal = Dimens.x2)
    ) {
        FilledButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            text = "Filled Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        FilledButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            text = "Filled Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.SECONDARY
        )

        FilledButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            text = "Filled Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.TERTIARY
        )

        FilledButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            enabled = false,
            text = "Filled Disabled Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        FilledButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            text = "Filled Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        FilledButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            text = "Filled Button",
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        FilledButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            text = "Filled Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        FilledButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            text = "Filled Button",
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        FilledButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            shape = CircleShape,
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        FilledButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            shape = CircleShape,
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Small,
            order = Order.PRIMARY
        )

        FilledButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            shape = CircleShape,
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.ExtraSmall,
            order = Order.PRIMARY
        )
    }
}
