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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.start

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItemEnumerated
import jp.co.soramitsu.common_wallet.presentation.compose.states.previewAssetItemCardStateList
import jp.co.soramitsu.feature_ecosystem_impl.presentation.BasicExploreCard
import jp.co.soramitsu.feature_ecosystem_impl.presentation.EcoSystemTokensState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun StartScreen(
    onCurrencyShowMore: () -> Unit,
    onPoolShowMore: () -> Unit,
    onTokenClicked: (String) -> Unit,
    viewModel: StartScreenViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.x2)
    ) {
        Text(
            modifier = Modifier.padding(
                start = Dimens.x2,
                end = Dimens.x2,
                top = Dimens.x3,
                bottom = Dimens.x2
            ),
            text = stringResource(id = R.string.common_explore),
            style = MaterialTheme.customTypography.headline1,
            color = MaterialTheme.customColors.fgPrimary,
        )
        StartScreenInternal(
            viewModel.state.collectAsStateWithLifecycle().value,
            onCurrencyShowMore,
            onPoolShowMore,
            onTokenClicked,
        )
    }
}

@Composable
private fun StartScreenInternal(
    state: EcoSystemTokensState,
    onCurrencyShowMore: () -> Unit,
    onPoolShowMore: () -> Unit,
    onTokenClicked: (String) -> Unit,
) {
    BasicExploreCard(
        title = stringResource(id = R.string.common_currencies),
        description = stringResource(id = R.string.explore_swap_tokens_on_sora),
        onShowMore = onCurrencyShowMore,
        content = {
            state.topTokens.forEach { pair ->
                AssetItemEnumerated(
                    modifier = Modifier.padding(vertical = Dimens.x1),
                    assetState = pair.second,
                    number = pair.first,
                    testTag = "",
                    onClick = onTokenClicked,
                )
            }
        },
    )
    Divider(
        color = Color.Transparent,
        modifier = Modifier.height(Dimens.x2)
    )
    BasicExploreCard(
        title = stringResource(id = R.string.discovery_polkaswap_pools),
        description = stringResource(id = R.string.explore_provide_and_earn),
        onShowMore = onPoolShowMore,
        content = {
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun DiscoverScreenPreview() {
    Column {
        StartScreenInternal(
            EcoSystemTokensState(
                previewAssetItemCardStateList.mapIndexed { i, a ->
                    i.toString() to a
                },
                "",
            ),
            {}, {}, {},
        )
    }
}
