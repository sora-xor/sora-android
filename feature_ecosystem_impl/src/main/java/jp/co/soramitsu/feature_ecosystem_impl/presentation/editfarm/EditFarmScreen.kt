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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.components.DetailsItem
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.presentation.compose.components.PolkaswapSlider
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.feature_ecosystem_impl.R
import jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.model.EditFarmScreenState
import jp.co.soramitsu.ui_core.component.button.FilledPolkaswapButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun EditFarmScreen(
    state: EditFarmScreenState,
    onSliderValueChange: (Double) -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (state.isCardLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(Dimens.x6)
                    .padding(Dimens.x1),
                color = MaterialTheme.customColors.accentPrimary
            )
        } else {
            ContentCard(
                modifier = Modifier
                    .padding(vertical = Dimens.x1, horizontal = Dimens.x2)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                innerPadding = PaddingValues(Dimens.x3),
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.select_pool_share),
                        style = MaterialTheme.customTypography.headline2
                    )

                    Divider(
                        color = Color.Transparent,
                        thickness = Dimens.x5
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = state.percentageText,
                            style = MaterialTheme.customTypography.displayL
                        )

                        TonalButton(
                            size = Size.ExtraSmall,
                            order = Order.PRIMARY,
                            text = stringResource(id = R.string.common_max).uppercase(),
                            onClick = { onSliderValueChange(1.0) }
                        )
                    }

                    PolkaswapSlider(
                        value = state.sliderProgressState,
                        onValueChange = { onSliderValueChange(it.toDouble()) }
                    )

                    DetailsItem(
                        modifier = Modifier.padding(bottom = Dimens.x1_5),
                        text = stringResource(id = R.string.polkaswap_farming_pool_share),
                        value1 = state.poolShareStaked,
                    )

                    Divider(
                        modifier = Modifier.padding(bottom = Dimens.x1_5),
                        thickness = 1.dp,
                        color = MaterialTheme.customColors.bgPage
                    )

                    DetailsItem(
                        modifier = Modifier.padding(bottom = Dimens.x1_5),
                        text = stringResource(id = R.string.polkaswap_farming_pool_share_will_be),
                        value1 = state.poolShareStakedWillBe
                    )

                    Divider(
                        modifier = Modifier.padding(bottom = Dimens.x1_5),
                        thickness = 1.dp,
                        color = MaterialTheme.customColors.bgPage
                    )

                    DetailsItem(
                        modifier = Modifier.padding(bottom = Dimens.x1_5),
                        text = stringResource(id = R.string.common_fee),
                        hint = stringResource(id = R.string.demeter_farming_deposit_fee_hint),
                        value1 = state.fee
                    )

                    Divider(
                        modifier = Modifier.padding(bottom = Dimens.x1_5),
                        thickness = 1.dp,
                        color = MaterialTheme.customColors.bgPage
                    )

                    DetailsItemNetworkFee(
                        modifier = Modifier.padding(bottom = Dimens.x1_5),
                        fee = state.networkFee,
                    )

                    Divider(
                        modifier = Modifier.padding(bottom = Dimens.x1_5),
                        thickness = 1.dp,
                        color = Color.Transparent
                    )

                    LoaderWrapper(
                        modifier = Modifier
                            .fillMaxWidth(),
                        loading = state.isButtonLoading,
                        loaderSize = Size.Large
                    ) { modifier, elevation ->
                        FilledPolkaswapButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(id = R.string.common_confirm),
                            size = Size.Large,
                            order = Order.PRIMARY,
                            enabled = state.isButtonActive,
                            onClick = { onConfirm() }
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
        EditFarmScreen(
            state = EditFarmScreenState(
                StringTriple("", "", ""),
                "54%",
                0.54f,
                "2.95 %",
                "5.95 %",
                "0.7",
                "0.9",
            ),
            { },
            { }
        )
    }
}
