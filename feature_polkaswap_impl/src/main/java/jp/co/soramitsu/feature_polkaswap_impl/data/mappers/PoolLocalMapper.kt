/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_polkaswap_impl.data.mappers

import java.math.BigDecimal
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
        kensetsuIncluded: BigDecimal? = null,
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
                kensetsuIncluded = kensetsuIncluded,
            ),
        )
    }
}
