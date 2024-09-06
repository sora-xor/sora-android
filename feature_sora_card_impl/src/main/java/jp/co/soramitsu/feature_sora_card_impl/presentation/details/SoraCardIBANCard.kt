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

package jp.co.soramitsu.feature_sora_card_impl.presentation.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.underlineSubstring
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.oauth.clients.ClientsFacade
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

data class SoraCardIBANCardState(
    val iban: String,
    val closed: Boolean,
)

@Composable
fun SoraCardIBANCard(
    soraCardIBANCardState: SoraCardIBANCardState,
    onShareClick: () -> Unit,
    onCardClick: () -> Unit,
) {
    ContentCard(
        modifier = remember {
            Modifier.testTagAsId("IbanCardClick")
        },
        cornerRadius = Dimens.x4,
        onClick = onCardClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = Dimens.x2)
        ) {
            Row(
                modifier = Modifier
                    .padding(all = Dimens.x1)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier
                        .wrapContentSize(),
                    text = stringResource(id = R.string.sora_card_iban_headline),
                    style = MaterialTheme.customTypography.headline2,
                    color = MaterialTheme.customColors.fgPrimary,
                    textAlign = TextAlign.Center
                )
                if (soraCardIBANCardState.iban.isNotEmpty() && soraCardIBANCardState.closed.not())
                    Icon(
                        modifier = Modifier
                            .testTagAsId("IbanCardShareClick")
                            .clickable(onClick = onShareClick)
                            .wrapContentSize(),
                        painter = painterResource(id = R.drawable.ic_rectangular_arrow_up),
                        contentDescription = null,
                        tint = MaterialTheme.customColors.fgSecondary
                    )
            }
            if (soraCardIBANCardState.closed) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(all = Dimens.x1),
                    text = underlineSubstring(stringResource(id = jp.co.soramitsu.oauth.R.string.iban_frozen_description, ClientsFacade.TECH_SUPPORT), ClientsFacade.TECH_SUPPORT),
                    style = MaterialTheme.customTypography.textS,
                    color = MaterialTheme.customColors.fgSecondary,
                )
            } else if (soraCardIBANCardState.iban.isNotEmpty()) {
                Text(
                    modifier = Modifier
                        .testTagAsId("IbanValueText")
                        .fillMaxWidth()
                        .padding(all = Dimens.x1),
                    text = soraCardIBANCardState.iban,
                    style = MaterialTheme.customTypography.textM,
                    color = MaterialTheme.customColors.fgPrimary,
                )
            } else {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(all = Dimens.x1),
                    text = underlineSubstring(stringResource(id = jp.co.soramitsu.oauth.R.string.iban_pending_description, ClientsFacade.TECH_SUPPORT), ClientsFacade.TECH_SUPPORT),
                    style = MaterialTheme.customTypography.textS,
                    color = MaterialTheme.customColors.fgSecondary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSoraCardIBANCard01() {
    SoraCardIBANCard(
        soraCardIBANCardState = SoraCardIBANCardState(
            iban = "LT61 3250 0467 7252 5583",
            closed = false,
        ),
        onShareClick = {},
        onCardClick = {},
    )
}

@Preview
@Composable
private fun PreviewSoraCardIBANCard02() {
    SoraCardIBANCard(
        soraCardIBANCardState = SoraCardIBANCardState(
            iban = "",
            closed = false,
        ),
        onShareClick = {},
        onCardClick = {},
    )
}

@Preview
@Composable
private fun PreviewSoraCardIBANCard03() {
    SoraCardIBANCard(
        soraCardIBANCardState = SoraCardIBANCardState(
            iban = "",
            closed = true,
        ),
        onShareClick = {},
        onCardClick = {},
    )
}
