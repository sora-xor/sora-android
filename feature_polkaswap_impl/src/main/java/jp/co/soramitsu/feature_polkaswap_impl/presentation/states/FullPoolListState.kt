/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.states

import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState

internal data class FullPoolListState(
    val fiatSum: String,
    val list: PoolsListState,
)
