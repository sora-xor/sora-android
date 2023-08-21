package jp.co.soramitsu.common.view

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun AmountCardIcon(
    testTagId: String? = null,
    @DrawableRes res: Int,
    text: String,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val reusableButtonModifier = remember(testTagId) {
        Modifier.run {
            return@run if (testTagId == null)
                this else testTagAsId(testTagId)
        }.fillMaxSize()
    }

    Column(
        modifier = Modifier.wrapContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(Size.Large)) {
            BleachedButton(
                modifier = reusableButtonModifier,
                shape = RoundedCornerShape(49),
                enabled = isEnabled,
                size = Size.Large,
                order = Order.TERTIARY,
                leftIcon = painterResource(res),
                onClick = onClick,
            )
        }
        Text(
            modifier = Modifier
                .wrapContentSize()
                .padding(top = Dimens.x1),
            text = text,
            style = MaterialTheme.customTypography.textXSBold,
            color = MaterialTheme.customColors.fgSecondary,
        )
    }
}
