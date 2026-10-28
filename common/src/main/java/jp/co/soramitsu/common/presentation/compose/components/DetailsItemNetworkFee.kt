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

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R

@Composable
fun DetailsItemNetworkFee(
    modifier: Modifier = Modifier,
    fee: String,
    feeFiat: String? = null,
) {
    DetailsItem(
        modifier = modifier,
        text = stringResource(id = R.string.network_fee),
        hint = stringResource(id = R.string.polkaswap_network_fee_info),
        value1 = fee,
        value2 = feeFiat,
    )
}
