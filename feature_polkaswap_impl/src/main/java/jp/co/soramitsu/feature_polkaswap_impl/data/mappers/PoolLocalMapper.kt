/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.data.mappers

import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.ext.Big100
import jp.co.soramitsu.common_wallet.domain.model.PoolData
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.core_db.model.PoolLocal

object PoolLocalMapper {

    fun mapLocal(poolLocal: PoolLocal, baseToken: Token, token: Token): PoolData {
        val basePooled = PolkaswapFormulas.calculatePooledValue(
            poolLocal.reservesFirst,
            poolLocal.poolProvidersBalance,
            poolLocal.totalIssuance,
        )
        val secondPooled = PolkaswapFormulas.calculatePooledValue(
            poolLocal.reservesSecond,
            poolLocal.poolProvidersBalance,
            poolLocal.totalIssuance,
        )
        val share = PolkaswapFormulas.calculateShareOfPool(
            poolLocal.poolProvidersBalance,
            poolLocal.totalIssuance,
        )
        val strategicBonusApy = poolLocal.strategicBonusApy?.multiply(Big100)
        return PoolData(
            token,
            baseToken,
            basePooled,
            poolLocal.reservesFirst,
            secondPooled,
            poolLocal.reservesSecond,
            strategicBonusApy?.toDouble(),
            share.toDouble(),
            poolLocal.totalIssuance,
            poolLocal.poolProvidersBalance,
            poolLocal.favorite,
        )
    }
}
