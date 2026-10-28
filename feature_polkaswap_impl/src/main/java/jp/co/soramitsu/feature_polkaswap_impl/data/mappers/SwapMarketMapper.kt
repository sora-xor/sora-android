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

package jp.co.soramitsu.feature_polkaswap_impl.data.mappers

import javax.inject.Inject
import jp.co.soramitsu.common.domain.Market

class SwapMarketMapper @Inject constructor() {

    fun mapMarket(market: List<String>): List<Market> = mutableListOf<Market>().apply {
        market.forEach { s ->
            val m = Market.values().find { it.backString == s }
            if (m != null) this.add(m)
        }
    }

    fun mapMarketsToStrings(market: List<Market>): List<String> =
        market.filter {
            it != Market.SMART
        }.map {
            it.backString
        }

    fun mapMarketsToFilter(markets: List<Market>): String =
        if (markets.isEmpty() || markets[0] == Market.SMART) "Disabled" else "AllowSelected"
}
