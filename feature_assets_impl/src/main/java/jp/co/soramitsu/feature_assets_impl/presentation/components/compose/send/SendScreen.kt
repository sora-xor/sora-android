/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.components.AccountWithIcon
import jp.co.soramitsu.common.presentation.compose.components.AssetAmountInput
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.previewDrawable
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun SendScreen(
    address: String,
    icon: Drawable?,
    onAddressClick: () -> Unit,
    onAddressLongClick: () -> Unit,
    inputState: AssetAmountInputState?,
    onAmountChange: (BigDecimal) -> Unit,
    onSelectToken: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onReviewClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    feeAmount: String,
    feeFiat: String,
    feeLoading: Boolean,
    reviewEnabled: Boolean,
) {
    SendScreenAddress(
        address = address,
        startIcon = icon,
        onAddressClick = onAddressClick,
        onAddressLongClick = onAddressLongClick
    )
    Divider(thickness = Dimens.x3, color = Color.Transparent)
    AssetAmountInput(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        focusRequester = focusRequester,
        state = inputState,
        onAmountChange = onAmountChange,
        onSelectToken = onSelectToken,
        onFocusChange = onFocusChange,
    )
    Divider(thickness = Dimens.x3, color = Color.Transparent)
    SendScreenNetworkFee(
        amount = feeAmount,
        fiat = feeFiat,
        loading = feeLoading,
    )
    Divider(thickness = Dimens.x3, color = Color.Transparent)
    FilledButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = reviewEnabled,
        text = stringResource(id = R.string.review),
        size = Size.Large,
        order = Order.PRIMARY,
        onClick = onReviewClick,
    )
}

@Composable
internal fun SendScreenNetworkFee(
    amount: String,
    fiat: String,
    loading: Boolean,
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        innerPadding = PaddingValues(Dimens.x3),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        ) {
            DetailsItemNetworkFee(
                fee = amount,
                feeFiat = fiat,
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = Dimens.x1)
                        .size(Dimens.x2)
                        .align(Alignment.Center),
                    color = MaterialTheme.customColors.fgSecondary,
                )
            }
        }
    }
}

@Composable
internal fun SendScreenAddress(
    address: String,
    startIcon: Drawable?,
    endIcon: Int? = null,
    onAddressClick: () -> Unit,
    onAddressLongClick: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        innerPadding = PaddingValues(Dimens.x3),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Text(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                text = stringResource(id = R.string.recipient_address).uppercase(),
                style = MaterialTheme.customTypography.headline4,
                color = MaterialTheme.customColors.fgSecondary,
            )
            Divider(thickness = Dimens.x2, color = Color.Transparent)
            AccountWithIcon(
                address = address,
                accountIcon = startIcon,
                rightIcon = endIcon ?: R.drawable.ic_chevron_right_24,
                onClick = onAddressClick,
                onLongClick = onAddressLongClick
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSendScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(12.dp)
    ) {
        SendScreen(
            address = "cnVkoowiejfskdfljk",
            icon = previewDrawable,
            onAddressClick = {},
            onAddressLongClick = {},
            inputState = previewAssetAmountInputState,
            onAmountChange = {},
            onSelectToken = {},
            onFocusChange = {},
            onReviewClick = {},
            feeAmount = "0.007 XOR",
            feeFiat = "~$0.2",
            feeLoading = true,
            reviewEnabled = false,
        )
    }
}