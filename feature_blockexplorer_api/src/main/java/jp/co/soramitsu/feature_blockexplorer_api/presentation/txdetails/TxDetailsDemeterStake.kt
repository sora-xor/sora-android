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

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun TxDetailsDemeterStake(
    modifier: Modifier,
    state: BasicTxDetailsState,
    isAmountGreen: Boolean,
    amount1: String,
    amount2: String,
    icon1: String,
    icon2: String,
    icon3: String,
    onCloseClick: () -> Unit,
    onCopyClick: (String) -> Unit,
) {
    BasicTxDetails(
        modifier = modifier,
        state = state,
        imageContent = {
            ConstraintLayout(
                modifier = Modifier.wrapContentSize()
            ) {
                val (token1, token2, token3) = createRefs()
                TokenIcon(
                    modifier = Modifier.constrainAs(token1) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                    },
                    uri = icon1,
                    size = 48.dp,
                )
                TokenIcon(
                    modifier = Modifier.constrainAs(token2) {
                        top.linkTo(token1.top, margin = 24.dp)
                        start.linkTo(token1.start, margin = 24.dp)
                    },
                    uri = icon2,
                    size = 48.dp,
                )
//                TokenIcon(
//                    modifier = Modifier.constrainAs(token3) {
//                        bottom.linkTo(token1.bottom)
//                        end.linkTo(token1.end)
//                    },
//                    uri = icon3,
//                    size = 24.dp,
//                )
            }
        },
        amountContent = {
            Column(
                modifier = Modifier
                    .padding(bottom = Dimens.x3)
                    .align(Alignment.Center)
                    .wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "%s %s".format(amount1, amount2),
                    color = if (isAmountGreen) MaterialTheme.customColors.statusSuccess else MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.headline3
                )
            }
        },
        onCloseClick = onCloseClick,
        onCopy = onCopyClick,
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewTxDetailsAddLiquidity() {
    TxDetailsDemeterStake(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        state = previewBasicTxDetailsItem,
        isAmountGreen = true,
        amount1 = "123.23 XOR",
        amount2 = "87987.23 VAL",
        icon1 = DEFAULT_ICON_URI,
        icon2 = DEFAULT_ICON_URI,
        icon3 = DEFAULT_ICON_URI,
        onCopyClick = {},
        onCloseClick = {},
    )
}