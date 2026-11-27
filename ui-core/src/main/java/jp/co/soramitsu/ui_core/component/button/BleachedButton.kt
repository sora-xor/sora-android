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
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.lit.Lit
import jp.co.soramitsu.ui_core.theme.customColors

/** A soft pad raised from the surface, with its label in the accent's colour. */
@Composable
fun BleachedButton(
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
    val colors = ButtonColors.bleachedButtonColors(order)
    val face = LitButtonFace(
        glass = null,
        content = colors.contentColor(true).value,
        disabledFill = colors.backgroundColor(false).value,
        disabledContent = colors.contentColor(false).value,
    )
    LitButton(modifier, shape, size, face, enabled, text, leftIcon, rightIcon, maxLines, onClick)
}

@Preview
@Composable
private fun PreviewBleachedButton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.customColors.bgPage)
            .padding(horizontal = Dimens.x2)
    ) {
        BleachedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            text = "Bleached Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        BleachedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            text = "Bleached Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.SECONDARY
        )

        BleachedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            text = "Bleached Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.TERTIARY
        )

        BleachedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            enabled = false,
            text = "Bleached Disabled Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        BleachedButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            text = "Bleached Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        BleachedButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            text = "Bleached Button",
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        BleachedButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            text = "Bleached Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        BleachedButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            text = "Bleached Button",
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        BleachedButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            shape = CircleShape,
            leftIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY
        )

        BleachedButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            shape = CircleShape,
            leftIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Small,
            order = Order.PRIMARY
        )

        BleachedButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            shape = CircleShape,
            leftIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.ExtraSmall,
            order = Order.PRIMARY
        )
    }
}
