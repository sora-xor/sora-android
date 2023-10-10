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
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.BasicBannerCard
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme

@Composable
fun BuyXorCard(
    onCloseCard: () -> Unit,
    onBuyXorClicked: () -> Unit,
) {
    BasicBannerCard(
        imageContent = R.drawable.ic_buy_xor_banner_sora,
        title = stringResource(id = R.string.buy_crypto_buy_xor_banner_title),
        description = stringResource(id = R.string.buy_crypto_buy_xor_with_fiat_subtitle),
        button = stringResource(id = R.string.common_buy_xor),
        closeEnabled = true,
        onButtonClicked = onBuyXorClicked,
        onCloseCard = onCloseCard,
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewBuyXorCard01() {
    SoraAppTheme {
        BuyXorCard(
            onCloseCard = {},
            onBuyXorClicked = {},
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewBuyXorCard02() {
    SoraAppTheme {
        BuyXorCard(
            onCloseCard = {},
            onBuyXorClicked = {},
        )
    }
}
