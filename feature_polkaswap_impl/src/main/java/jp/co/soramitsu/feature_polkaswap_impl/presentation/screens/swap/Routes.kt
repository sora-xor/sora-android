/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.swap

internal object SwapRoutes {
    const val selectTokenParamName = "token"
    enum class SelectTokenParam(val path: String) {
        TO("to"), FROM("from")
    }

    const val start = "Swap.Main"
    const val slippage = "Swap.Slippage"
    const val selectToken = "Swap.SelectToken/{$selectTokenParamName}"
    fun buildSelectTokenRoute(param: SelectTokenParam) = "Swap.SelectToken/${param.path}"
    const val markets = "Swap.SelectMarket"
    const val confirm = "Swap.Confirm"
    const val disclaimer = "Swap.Disclaimer"
}
