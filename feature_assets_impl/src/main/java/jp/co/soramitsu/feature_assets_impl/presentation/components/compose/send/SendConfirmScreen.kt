/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
