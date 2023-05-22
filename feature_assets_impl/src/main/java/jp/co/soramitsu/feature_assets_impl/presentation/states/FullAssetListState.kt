/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.states

import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState

data class FullAssetListState(
    val searchMode: Boolean,
    val fiatSum: String,
    val topList: List<AssetItemCardState>,
    val bottomList: List<AssetItemCardState>,
)
