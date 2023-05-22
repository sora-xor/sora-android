/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.components.AssetAmountInput
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.previewDrawable
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun SendConfirmScreen(
    address: String,
    icon: Drawable?,
    onAddressClick: () -> Unit,
    onAddressLongClick: () -> Unit,
    inputState: AssetAmountInputState?,
    onConfirmClick: () -> Unit,
    inProgress: Boolean,
    feeAmount: String,
    feeFiat: String,
    reviewEnabled: Boolean,
) {
    SendScreenAddress(
        address = address,
        startIcon = icon,
        endIcon = R.drawable.ic_copy_24,
        onAddressClick = onAddressClick,
        onAddressLongClick = onAddressLongClick
    )
    Divider(thickness = Dimens.x2, color = Color.Transparent)
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(Dimens.x1),
        ) {
            Text(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(top = Dimens.x2, start = Dimens.x2, end = Dimens.x2),
                text = stringResource(id = R.string.asset_to_send).uppercase(),
                style = MaterialTheme.customTypography.headline4,
                color = MaterialTheme.customColors.fgSecondary,
            )
            AssetAmountInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                state = inputState,
                onAmountChange = {},
                onSelectToken = {},
                onFocusChange = {},
            )
        }
    }
    Divider(thickness = Dimens.x2, color = Color.Transparent)
    SendScreenNetworkFee(
        amount = feeAmount,
        fiat = feeFiat,
        loading = false,
    )
    Divider(thickness = Dimens.x2, color = Color.Transparent)
    LoaderWrapper(
        modifier = Modifier.fillMaxWidth(),
        loading = inProgress,
        loaderSize = Size.Large,
    ) { modifier, elevation ->
        FilledButton(
            modifier = modifier,
            enabled = reviewEnabled,
            text = stringResource(id = R.string.common_confirm),
            size = Size.Large,
            order = Order.PRIMARY,
            onClick = onConfirmClick,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSendConfirmScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(12.dp)
    ) {
        SendConfirmScreen(
            address = "cnVkoowiejfskdfljk",
            icon = previewDrawable,
            onAddressClick = {},
            onAddressLongClick = {},
            onConfirmClick = {},
            inputState = previewAssetAmountInputState,
            inProgress = false,
            feeAmount = "0.007 XOR",
            feeFiat = "~$0.2",
            reviewEnabled = false,
        )
    }
}
