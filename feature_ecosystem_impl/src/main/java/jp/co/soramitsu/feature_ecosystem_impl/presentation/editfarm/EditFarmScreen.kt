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
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.components.DetailsItem
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.presentation.compose.components.PolkaswapSlider
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.feature_ecosystem_impl.R
import jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.model.EditFarmScreenState
import jp.co.soramitsu.common.presentation.compose.neon.FilledPolkaswapButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.common.presentation.compose.neon.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
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
    ) {
        if (state.isCardLoading) {
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
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                innerPadding = PaddingValues(Dimens.x3),
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.select_pool_share),
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary
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
                            style = MaterialTheme.customTypography.displayL,
                            color = MaterialTheme.customColors.fgPrimary
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
                            enabled = state.isButtonActive && !LocalSelectedAccountReadOnly.current,
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
