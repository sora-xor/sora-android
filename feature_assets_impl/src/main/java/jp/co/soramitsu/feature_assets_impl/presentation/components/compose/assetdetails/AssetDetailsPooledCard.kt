/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
