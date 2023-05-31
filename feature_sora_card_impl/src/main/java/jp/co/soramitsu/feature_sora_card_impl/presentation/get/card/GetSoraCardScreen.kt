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

package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun GetSoraCardScreen(
    scrollState: ScrollState,
    state: GetSoraCardState,
    onBlackList: () -> Unit,
    onEnableCard: () -> Unit,
    onGetMoreXor: () -> Unit,
    onAlreadyHaveCard: () -> Unit,
    onDismissGetMoreXorAlert: () -> Unit,
    onBuyCrypto: () -> Unit,
    onSwap: () -> Unit,
    onEuroClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Dimens.x2)
            .padding(bottom = Dimens.x5)
    ) {
        ContentCard(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.x2)
            ) {
                Image(
                    modifier = Modifier.fillMaxWidth(),
                    painter = painterResource(R.drawable.sora_card),
                    contentDescription = null
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x2, start = Dimens.x1, end = Dimens.x1),
                    text = stringResource(R.string.sora_card_title),
                    style = MaterialTheme.customTypography.headline2,
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x2, start = Dimens.x1, end = Dimens.x1),
                    text = stringResource(jp.co.soramitsu.oauth.R.string.details_description),
                    style = MaterialTheme.customTypography.paragraphM,
                )

                AnnualFee()

                FreeCardIssuance(state, onEuroClick)

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x2, start = Dimens.x1, end = Dimens.x1),
                    text = stringResource(R.string.sora_card_blacklisted_countires_warning),
                    style = MaterialTheme.customTypography.paragraphXS.copy(textAlign = TextAlign.Center),
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTagAsId("SoraCardResidents")
                        .padding(start = Dimens.x1, end = Dimens.x1)
                        .clickable(onClick = onBlackList),
                    text = stringResource(jp.co.soramitsu.oauth.R.string.unsupported_countries_link),
                    style = MaterialTheme.customTypography.paragraphXS.copy(
                        textAlign = TextAlign.Center,
                        textDecoration = TextDecoration.Underline
                    ),
                    color = MaterialTheme.customColors.statusError,
                )

                if (state.enoughXor) {
                    FilledButton(
                        modifier = Modifier
                            .testTagAsId("SoraCardContinue")
                            .fillMaxWidth()
                            .padding(vertical = Dimens.x2, horizontal = Dimens.x1),
                        text = stringResource(R.string.common_continue),
                        size = Size.Large,
                        enabled = state.xorRatioAvailable && state.connection,
                        order = Order.PRIMARY,
                        onClick = onEnableCard,
                    )
                } else {
                    FilledButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTagAsId("SoraCardLogInOrSignUp")
                            .padding(vertical = Dimens.x2, horizontal = Dimens.x1),
                        text = stringResource(R.string.sora_card_log_in_or_sign_up),
                        size = Size.Large,
                        enabled = state.xorRatioAvailable && state.connection,
                        order = Order.PRIMARY,
                        onClick = onGetMoreXor,
                    )
                }
            }
        }

        if (state.getMorXorAlert) {
            GetMorXorAlert(
                onDismiss = onDismissGetMoreXorAlert,
                onBuyCrypto = onBuyCrypto,
                onSwap = onSwap,
            )
        }
    }
}

@Composable
private fun AnnualFee() {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.x2, start = Dimens.x1, end = Dimens.x1)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.padding(start = Dimens.x3),
                painter = painterResource(R.drawable.ic_check_rounded),
                contentDescription = null,
                tint = Color.Unspecified
            )

            Text(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = Dimens.x2, bottom = Dimens.x2, start = Dimens.x1, end = Dimens.x3
                    ),
                text = AnnotatedString(
                    text = stringResource(R.string.sora_card_annual_service_fee),
                    spanStyles = listOf(
                        AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), 0, 3)
                    )
                ),
                style = MaterialTheme.customTypography.textL
            )
        }
    }
}

@Composable
private fun FreeCardIssuance(
    state: GetSoraCardState,
    onEuroClick: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.x2, start = Dimens.x1, end = Dimens.x1)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x2, horizontal = Dimens.x3)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(end = Dimens.x1),
                    painter = if (state.enoughXor) {
                        painterResource(R.drawable.ic_check_rounded)
                    } else {
                        painterResource(R.drawable.ic_cross_24)
                    },
                    contentDescription = null,
                    tint = if (state.enoughXor) {
                        Color.Unspecified
                    } else {
                        MaterialTheme.customColors.fgTertiary
                    }
                )

                Text(
                    modifier = Modifier.fillMaxSize(),
                    text = AnnotatedString(
                        text = stringResource(R.string.sora_card_free_card_issuance),
                        spanStyles = listOf(
                            AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), 0, 4)
                        )
                    ),
                    style = MaterialTheme.customTypography.textL
                )
            }

            Text(
                modifier = Modifier.fillMaxSize(),
                text = stringResource(R.string.sora_card_free_card_issuance_conditions_xor),
                style = MaterialTheme.customTypography.paragraphM
            )

            BalanceIndicator(
                modifier = Modifier
                    .testTagAsId("SoraCardBalanceIndicator")
                    .fillMaxWidth()
                    .padding(top = Dimens.x3, bottom = Dimens.x1),
                percent = state.percent.toFloat(),
                label = when {
                    !state.xorRatioAvailable -> {
                        stringResource(jp.co.soramitsu.oauth.R.string.cant_fetch_refresh)
                    }
                    state.enoughXor -> {
                        stringResource(R.string.sora_card_you_have_enough_xor)
                    }
                    else -> {
                        stringResource(
                            R.string.sora_card_you_need_xor,
                            state.needInXor,
                            state.needInEur,
                        )
                    }
                },
                onClick = onEuroClick,
            )
        }
    }
}

@Composable
private fun GetMorXorAlert(
    onDismiss: () -> Unit,
    onSwap: () -> Unit,
    onBuyCrypto: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.x3),
        buttons = {
            Column(
                modifier = Modifier
                    .padding(Dimens.x2)
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                TonalButton(
                    modifier = Modifier
                        .testTagAsId("BuyXorWithEur")
                        .fillMaxWidth(),
                    size = Size.Small,
                    order = Order.TERTIARY,
                    text = stringResource(id = R.string.sora_card_buy_xor_with_eur),
                ) {
                    onBuyCrypto()
                }
                Divider(thickness = Dimens.x2, color = Color.Transparent)
                TonalButton(
                    modifier = Modifier
                        .testTagAsId("SwapCryptoForXor")
                        .fillMaxWidth(),
                    size = Size.Small,
                    order = Order.TERTIARY,
                    text = stringResource(id = R.string.sora_card_swap_crypto_for_xor),
                ) {
                    onSwap()
                }
            }
        },
        title = {
            Text(
                text = stringResource(id = R.string.sora_card_get_more_xor),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            )
        },
    )
}

@Preview
@Composable
private fun PreviewGetSoraCardScreen() {
    GetSoraCardScreen(
        scrollState = rememberScrollState(),
        state = GetSoraCardState(),
        {}, {}, {}, {}, {}, {}, {}, {},
    )
}
