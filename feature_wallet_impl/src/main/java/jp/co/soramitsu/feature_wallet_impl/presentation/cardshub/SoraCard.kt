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

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.SoraCardImage
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.common_wallet.presentation.compose.states.SoraCardState
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun SoraCard(
    modifier: Modifier = Modifier,
    state: SoraCardState,
    onCardStateClicked: () -> Unit,
    onNeedUpdate: () -> Unit,
    onCloseClicked: () -> Unit,
) {
    val shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
    Box(
        modifier = modifier
            .clip(shape)
            .clickable(
                onClick = onCardStateClicked,
                enabled = state.loading.not() && state.needUpdate.not()
            )
    ) {
        SoraCardImage(
            modifier = Modifier.fillMaxWidth(),
            enabled = state.needUpdate.not(),
        )

        if (state.needUpdate) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = Dimens.x1)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.x2),
            ) {
                Text(
                    modifier = Modifier,
                    text = stringResource(id = jp.co.soramitsu.oauth.R.string.card_update_title),
                    style = MaterialTheme.customTypography.headline2,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                FilledButton(
                    modifier = Modifier
                        .wrapContentSize()
                        .testTagAsId("SoraCardUpdateApp"),
                    size = Size.Large,
                    order = Order.SECONDARY,
                    onClick = onNeedUpdate,
                    text = stringResource(jp.co.soramitsu.oauth.R.string.card_update_button),
                )
            }
        } else {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(bottom = Dimens.x2, end = Dimens.x2)
                        .size(Dimens.x5)
                        .background(
                            color = MaterialTheme.customColors.bgSurface,
                            shape = CircleShape
                        )
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = MaterialTheme.customColors.fgPrimary,
                )
            } else {
                CardStateButton(
                    modifier = Modifier
                        .wrapContentWidth()
                        .run {
                            if (state.success.not() && state.ibanBalance == null)
                                padding(bottom = Dimens.x3) else padding(all = Dimens.x1)
                        }
                        .run {
                            if (state.success.not() && state.ibanBalance == null)
                                align(Alignment.BottomCenter) else align(Alignment.BottomEnd)
                        },
                    kycStatus = state.kycStatus,
                    ibanInfo = state.ibanBalance,
                    success = state.success,
                    onCardStateClicked = onCardStateClicked,
                )
            }

            if (state.success.not() && state.ibanBalance == null)
                BleachedButton(
                    modifier = Modifier
                        .wrapContentWidth()
                        .align(Alignment.TopEnd)
                        .padding(Dimens.x1),
                    size = Size.ExtraSmall,
                    order = Order.TERTIARY,
                    shape = CircleShape,
                    onClick = onCloseClicked,
                    leftIcon = painterResource(jp.co.soramitsu.ui_core.R.drawable.ic_cross),
                )
        }
    }
}

@Composable
private fun CardStateButton(
    modifier: Modifier = Modifier,
    kycStatus: String?,
    ibanInfo: IbanInfo?,
    success: Boolean,
    onCardStateClicked: () -> Unit
) {
    if (ibanInfo != null) {
        BleachedButton(
            modifier = modifier
                .testTagAsId("CardInfo"),
            size = Size.ExtraSmall,
            order = Order.SECONDARY,
            onClick = onCardStateClicked,
            text = if (ibanInfo.active) ibanInfo.balance else ibanInfo.iban,
        )
    } else if (kycStatus == null) {
        FilledButton(
            modifier = modifier
                .testTagAsId("GetSoraCard"),
            size = Size.Large,
            order = Order.SECONDARY,
            onClick = onCardStateClicked,
            text = stringResource(R.string.get_sora_card_title),
        )
    } else {
        TonalButton(
            modifier = modifier
                .testTagAsId("SoraCardButton"),
            size = Size.Large,
            order = Order.TERTIARY,
            onClick = onCardStateClicked,
            text = if (success) "--" else kycStatus,
        )
    }
}

@Composable
@Preview(locale = "en")
private fun PreviewSoraCard1() {
    SoraCard(
        modifier = Modifier.fillMaxWidth(),
        state = SoraCardState(
            kycStatus = "",
//            ibanBalance = "${euroSign}345.3",
            ibanBalance = null,
            loading = false,
            success = true,
            needUpdate = false,
        ),
        onCloseClicked = {},
        onCardStateClicked = {},
        onNeedUpdate = {},
    )
}

@Composable
@Preview(locale = "he", uiMode = Configuration.UI_MODE_NIGHT_NO)
private fun PreviewSoraCard2() {
    SoraAppTheme {
        SoraCard(
            modifier = Modifier.fillMaxWidth(),
            state = SoraCardState(
                kycStatus = "Pending",
                ibanBalance = null,
                loading = false,
                success = false,
                needUpdate = false,
            ),
            onCloseClicked = {},
            onCardStateClicked = {},
            onNeedUpdate = {},
        )
    }
}

@Composable
@Preview(locale = "en", uiMode = Configuration.UI_MODE_NIGHT_NO)
private fun PreviewSoraCard3() {
    SoraAppTheme {
        SoraCard(
            modifier = Modifier.fillMaxWidth(),
            state = SoraCardState(
                kycStatus = null,
                ibanBalance = null,
                loading = false,
                success = false,
                needUpdate = false,
            ),
            onCloseClicked = {},
            onCardStateClicked = {},
            onNeedUpdate = {},
        )
    }
}

@Composable
@Preview(locale = "en", uiMode = Configuration.UI_MODE_NIGHT_NO)
private fun PreviewSoraCard5() {
    SoraAppTheme {
        SoraCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            state = SoraCardState(
                kycStatus = null,
                ibanBalance = null,
                loading = false,
                success = false,
                needUpdate = true,
            ),
            onCloseClicked = {},
            onCardStateClicked = {},
            onNeedUpdate = {},
        )
    }
}

@Composable
@Preview(locale = "en", uiMode = Configuration.UI_MODE_NIGHT_NO)
private fun PreviewSoraCard4() {
    SoraAppTheme {
        SoraCard(
            modifier = Modifier.fillMaxWidth(),
            state = SoraCardState(
                kycStatus = null,
                ibanBalance = null,
                loading = true,
                success = false,
                needUpdate = false,
            ),
            onCloseClicked = {},
            onCardStateClicked = {},
            onNeedUpdate = {},
        )
    }
}

@Composable
@Preview(locale = "en", uiMode = Configuration.UI_MODE_NIGHT_NO)
private fun PreviewSoraCard6() {
    SoraAppTheme {
        SoraCard(
            modifier = Modifier.fillMaxWidth(),
            state = SoraCardState(
                kycStatus = null,
                ibanBalance = IbanInfo(iban = "abcderr", active = true, balance = "45.5"),
                loading = false,
                success = false,
                needUpdate = false,
            ),
            onCloseClicked = {},
            onCardStateClicked = {},
            onNeedUpdate = {},
        )
    }
}
