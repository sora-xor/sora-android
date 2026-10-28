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

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.components.PoolsList
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
internal fun AssetDetailsPooledCard(
    title: String,
    state: PoolsListState,
    onPoolClick: (StringPair) -> Unit,
) {
    AssetDetailsCard(
        title = title,
        amount = "",
    ) {
        Spacer(Modifier.height(Dimens.x1))
        PoolsList(
            cardState = state,
            onPoolClick = onPoolClick
        )
    }
}
