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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityadd

internal object LiquidityAddRoutes {
    const val start = "Liquidity.Add.Start"
    const val confirm = "Liquidity.Add.Confirm"
    const val slippage = "Liquidity.Add.Slippage"
    const val selectTokenParamName = "token"
    enum class AddSelectTokenParam(val path: String) {
        T1("t1"),
        T2("t2")
    }
    const val selectToken = "Liquidity.Add.SelectToken/{$selectTokenParamName}"
    fun buildSelectTokenRoute(param: AddSelectTokenParam) = "Liquidity.Add.SelectToken/${param.path}"
}
