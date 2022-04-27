/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model

data class ButtonState(
    val text: String = "",
    val enabled: Boolean = false,
    val loading: Boolean = false
)
