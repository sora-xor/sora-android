/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
