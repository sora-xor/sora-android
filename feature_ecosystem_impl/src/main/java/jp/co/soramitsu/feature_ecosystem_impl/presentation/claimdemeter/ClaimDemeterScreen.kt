/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.feature_ecosystem_impl.R
import jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter.model.ClaimScreenState
import jp.co.soramitsu.common.presentation.compose.neon.FilledPolkaswapButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun ClaimDemeterScreen(
    state: ClaimScreenState,
    onClaimPressed: () -> Unit,
) {
    Box {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(Dimens.x6)
                    .padding(Dimens.x1),
                color = MaterialTheme.customColors.accentPrimary
            )
        } else {
            ContentCard(
                modifier = Modifier
                    .padding(vertical = Dimens.x1, horizontal = Dimens.x2)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .wrapContentHeight(),
                innerPadding = PaddingValues(Dimens.x3),
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.claim_rewards),
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary
                    )

                    Divider(
                        color = Color.Transparent,
                        thickness = Dimens.x3
                    )

                    TokenIcon(
                        modifier = Modifier
                            .size(Dimens.x9)
                            .align(Alignment.CenterHorizontally),
                        uri = state.tokenIcon,
                        size = Size.Large
                    )

                    Divider(
                        color = Color.Transparent,
                        thickness = Dimens.x1_5
                    )

                    Text(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        text = state.amountTitle,
                        style = MaterialTheme.customTypography.headline3,
                        color = MaterialTheme.customColors.fgPrimary
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        text = state.fiatAmountTitle,
                        style = MaterialTheme.customTypography.textXSBold,
                        color = MaterialTheme.customColors.fgSecondary
                    )

                    Divider(
                        color = Color.Transparent,
                        thickness = Dimens.x3
                    )

                    DetailsItemNetworkFee(fee = state.networkFeeText)

                    Divider(
                        color = Color.Transparent,
                        thickness = Dimens.x3
                    )

                    LoaderWrapper(
                        modifier = Modifier
                            .fillMaxWidth(),
                        loading = state.isButtonLoading,
                        loaderSize = Size.Large
                    ) { modifier, elevation ->
                        FilledPolkaswapButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(id = R.string.common_claim),
                            size = Size.Large,
                            order = Order.PRIMARY,
                            enabled = state.isButtonActive && !LocalSelectedAccountReadOnly.current,
                            onClick = onClaimPressed
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewEditFarms() {
    Column {
        ClaimDemeterScreen(
            state = ClaimScreenState(
                StringTriple("id1", "id2", "id3"),
                DEFAULT_ICON_URI,
                "100 DAI",
                "$100",
                "0.007 XOR",
                true,
                false,
                isLoading = false
            ),
            { }
        )
    }
}
