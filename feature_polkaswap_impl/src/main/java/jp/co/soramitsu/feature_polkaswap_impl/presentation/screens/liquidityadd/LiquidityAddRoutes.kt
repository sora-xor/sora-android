/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityadd

internal object LiquidityAddRoutes {
    const val start = "Liquidity.Add.Start"
    const val confirm = "Liquidity.Add.Confirm"
    const val slippage = "Liquidity.Add.Slippage"
    const val selectTokenParamName = "token"
    enum class AddSelectTokenParam(val path: String) {
        T1("t1"), T2("t2")
    }
    const val selectToken = "Liquidity.Add.SelectToken/{$selectTokenParamName}"
    fun buildSelectTokenRoute(param: AddSelectTokenParam) = "Liquidity.Add.SelectToken/${param.path}"
}
