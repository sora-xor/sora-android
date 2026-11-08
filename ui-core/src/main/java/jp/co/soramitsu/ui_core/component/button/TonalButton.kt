package jp.co.soramitsu.ui_core.component.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalRippleConfiguration
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RippleConfiguration
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.button.properties.buttonElevation
import jp.co.soramitsu.ui_core.component.button.properties.buttonSize
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TonalButton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MaterialTheme.borderRadius.ml),
    size: Dp,
    order: Order,
    text: String? = null,
    enabled: Boolean = true,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(
        LocalRippleConfiguration provides RippleConfiguration(
            color = ButtonColors.rippleColor(order),
            rippleAlpha = null,
        )
    ) {
        Button(
            modifier = modifier.buttonSize(size, shape, false, enabled),
            colors = ButtonColors.tonalButtonColors(order),
            shape = shape,
            enabled = enabled,
            onClick = onClick,
            elevation = buttonElevation(size, false),
            contentPadding = PaddingValues(0.dp),
        ) {
            leftIcon?.let { painter ->
                Icon(
                    modifier = Modifier.padding(
                        start = text?.let { Dimens.x2 } ?: 0.dp,
                        end = text?.let { Dimens.x1 } ?: 0.dp
                    ),
                    painter = painter,
                    contentDescription = null
                )
            }

            text?.let {
                Text(
                    modifier = Modifier.padding(
                        start = leftIcon?.let { 0.dp } ?: Dimens.x3,
                        end = rightIcon?.let { 0.dp } ?: Dimens.x3,
                        top = Dimens.x1,
                        bottom = Dimens.x1
                    ),
                    textAlign = TextAlign.Center,
                    text = text,
                    style = MaterialTheme.customTypography.buttonM,
                    maxLines = maxLines,
                )
            }

            rightIcon?.let { painter ->
                Icon(
                    modifier = Modifier.padding(
                        start = text?.let { Dimens.x1 } ?: 0.dp,
                        end = text?.let { Dimens.x2 } ?: 0.dp
                    ),
                    painter = painter,
                    contentDescription = null
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewTonalButton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.customColors.bgPage)
            .padding(horizontal = Dimens.x2)
    ) {
        TonalButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            text = "Tonal Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY,
        )

        TonalButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            text = "Tonal Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.SECONDARY,
        )

        TonalButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            text = "Tonal Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.TERTIARY,
        )

        TonalButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1),
            enabled = false,
            text = "Tonal Disabled Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY,
        )

        TonalButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            text = "Tonal Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY,
        )

        TonalButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            text = "Tonal Button",
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY,
        )

        TonalButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            text = "Tonal Button",
            leftIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY,
        )

        TonalButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            text = "Tonal Button",
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Large,
            order = Order.PRIMARY,
        )

        TonalButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            text = "Tonal Button",
            onClick = {},
            size = Size.ExtraSmall,
            order = Order.PRIMARY,
        )

        TonalButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            text = "Tonal Button",
            rightIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.ExtraSmall,
            order = Order.PRIMARY,
        )

        TonalButton(
            modifier = Modifier.padding(vertical = Dimens.x1),
            enabled = true,
            shape = CircleShape,
            leftIcon = painterResource(R.drawable.ic_plus),
            onClick = {},
            size = Size.Small,
            order = Order.PRIMARY,
        )
    }
}
