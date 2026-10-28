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

import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.common_wallet.domain.model.BasicPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.UserPoolData
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.core_db.model.BasicPoolLocal
import jp.co.soramitsu.core_db.model.BasicPoolWithTokenFiatLocal
import jp.co.soramitsu.core_db.model.UserPoolJoinedLocal

object PoolLocalMapper {

    fun mapBasicToPoolData(
        basicPoolLocal: BasicPoolLocal,
        baseToken: Token,
        token: Token,
        apy: Double?,
    ): BasicPoolData {
        return BasicPoolData(
            baseToken = baseToken,
            targetToken = token,
            baseReserves = basicPoolLocal.reserveBase,
            targetReserves = basicPoolLocal.reserveTarget,
            totalIssuance = basicPoolLocal.totalIssuance,
            reserveAccount = basicPoolLocal.reservesAccount,
            sbapy = apy,
        )
    }

    suspend fun mapBasicPoolTokenFiatLocal(
        basic: BasicPoolWithTokenFiatLocal,
        mapper: AssetLocalToAssetMapper,
        sbapy: (String) -> Double?,
    ): BasicPoolData {
        return BasicPoolData(
            baseToken = mapper.map(basic.tokenBaseLocal),
            targetToken = mapper.map(basic.tokenTargetLocal),
            baseReserves = basic.basicPoolLocal.reserveBase,
            targetReserves = basic.basicPoolLocal.reserveTarget,
            totalIssuance = basic.basicPoolLocal.totalIssuance,
            reserveAccount = basic.basicPoolLocal.reservesAccount,
            sbapy = sbapy(basic.basicPoolLocal.reservesAccount),
        )
    }

    fun mapLocal(
        poolLocal: UserPoolJoinedLocal,
        baseToken: Token,
        token: Token,
        apy: Double?,
    ): CommonUserPoolData {
        val basePooled = PolkaswapFormulas.calculatePooledValue(
            poolLocal.basicPoolLocal.reserveBase,
            poolLocal.userPoolLocal.poolProvidersBalance,
            poolLocal.basicPoolLocal.totalIssuance,
            baseToken.precision,
        )
        val secondPooled = PolkaswapFormulas.calculatePooledValue(
            poolLocal.basicPoolLocal.reserveTarget,
            poolLocal.userPoolLocal.poolProvidersBalance,
            poolLocal.basicPoolLocal.totalIssuance,
            token.precision,
        )
        val share = PolkaswapFormulas.calculateShareOfPoolFromAmount(
            poolLocal.userPoolLocal.poolProvidersBalance,
            poolLocal.basicPoolLocal.totalIssuance,
        )
        return CommonUserPoolData(
            basic = BasicPoolData(
                baseToken = baseToken,
                targetToken = token,
                baseReserves = poolLocal.basicPoolLocal.reserveBase,
                targetReserves = poolLocal.basicPoolLocal.reserveTarget,
                totalIssuance = poolLocal.basicPoolLocal.totalIssuance,
                reserveAccount = poolLocal.basicPoolLocal.reservesAccount,
                sbapy = apy,
            ),
            user = UserPoolData(
                basePooled = basePooled,
                targetPooled = secondPooled,
                poolShare = share,
                poolProvidersBalance = poolLocal.userPoolLocal.poolProvidersBalance,
                favorite = poolLocal.userPoolLocal.favorite,
                sort = poolLocal.userPoolLocal.sortOrder,
            ),
        )
    }
}
