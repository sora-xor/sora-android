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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun SwapAmountSquare(
    modifier: Modifier,
    icon: Uri,
    amount: String,
    amountFiat: String,
) {
    ContentCard(
        modifier = modifier,
        innerPadding = PaddingValues(vertical = Dimens.x3),
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(icon).build(),
                modifier = Modifier
                    .size(size = Size.Small),
                contentDescription = null,
                imageLoader = LocalContext.current.imageLoader,
            )
            Text(
                modifier = Modifier
                    .padding(vertical = Dimens.x1)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = Dimens.x1),
                text = amount,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = MaterialTheme.customTypography.displayS,
            )
            Text(
                modifier = Modifier
                    .wrapContentSize(),
                text = amountFiat,
                maxLines = 1,
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    Row {
        SwapAmountSquare(
            modifier = Modifier.weight(1f),
            icon = DEFAULT_ICON_URI,
            amount = "123.144 XOR",
            amountFiat = "$234.34",
        )
        Spacer(modifier = Modifier.size(Dimens.x2))
        SwapAmountSquare(
            modifier = Modifier.weight(1f),
            icon = DEFAULT_ICON_URI,
            amount = "123.144 XOR",
            amountFiat = "$234.34",
        )
    }
}
