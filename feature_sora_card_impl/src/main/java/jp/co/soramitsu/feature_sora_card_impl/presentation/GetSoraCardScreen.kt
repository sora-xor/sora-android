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

package jp.co.soramitsu.feature_sora_card_impl.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.SoraCardImage
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TextButton
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
    onSignUp: () -> Unit,
    onLogIn: () -> Unit,
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
                SoraCardImage(
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x2, start = Dimens.x1, end = Dimens.x1),
                    text = stringResource(R.string.sora_card_title),
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.headline2,
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x2, start = Dimens.x1, end = Dimens.x1),
                    text = stringResource(jp.co.soramitsu.oauth.R.string.details_description),
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.paragraphM,
                )

                AnnualFee()

                FreeCardIssuance()

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x2, start = Dimens.x1, end = Dimens.x1),
                    text = stringResource(R.string.sora_card_blacklisted_countires_warning),
                    color = MaterialTheme.customColors.fgPrimary,
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
                FilledButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTagAsId("SoraCardSignUp")
                        .padding(vertical = Dimens.x2, horizontal = Dimens.x1),
                    text = "Sign up for SORA Card",
                    size = Size.Large,
                    enabled = state.xorRatioAvailable && state.connection,
                    order = Order.PRIMARY,
                    onClick = onSignUp,
                )
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTagAsId("SoraCardHaveCard")
                        .padding(horizontal = Dimens.x1),
                    size = Size.Large,
                    enabled = state.xorRatioAvailable && state.connection,
                    order = Order.PRIMARY,
                    text = stringResource(id = jp.co.soramitsu.oauth.R.string.details_already_have_card),
                    onClick = onLogIn,
                )
            }
        }
    }
}

@Composable
private fun AnnualFee() {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.x2, start = Dimens.x1, end = Dimens.x1),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(
                    top = Dimens.x2, bottom = Dimens.x2, start = Dimens.x3, end = Dimens.x3,
                ),
            text = stringResource(R.string.sora_card_annual_service_fee),
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.textL,
        )
    }
}

@Composable
private fun FreeCardIssuance() {
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
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = stringResource(R.string.sora_card_free_card_issuance),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textL,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewGetSoraCardScreen() {
    GetSoraCardScreen(
        scrollState = rememberScrollState(),
        state = GetSoraCardState(applicationFee = "29", connection = true, xorRatioAvailable = true),
        {}, {}, {},
    )
}
