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

package jp.co.soramitsu.test_data

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common_wallet.domain.model.BasicPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.UserPoolData

object PolkaswapTestData {

    val XOR_ASSET = TestAssets.xorAsset(BigDecimal.ONE)

    val XOR_ASSET_ZERO_BALANCE = TestAssets.xorAsset(BigDecimal.ZERO)

    val VAL_ASSET = TestAssets.valAsset(BigDecimal.ONE)

    val XSTXAU_ASSET = TestAssets.xstxauAsset(BigDecimal.ONE)

    val LIQUIDITY_DATA = LiquidityData(
        firstReserves = BigDecimal.ONE,
        secondReserves = BigDecimal.ONE,
        secondPooled = BigDecimal.ONE
    )

    val BASIC_POOL_DATA = BasicPoolData(
        XOR_ASSET.token,
        VAL_ASSET.token,
        BigDecimal.TEN,
        BigDecimal.TEN,
        BigDecimal.TEN,
        "",
        1.0,
    )

    val USER_POOL_DATA = UserPoolData(
        BigDecimal.ONE,
        BigDecimal.ONE,
        10.0,
        BigDecimal.TEN,
        true,
        2,
    )

    val COMMON_POOL_DATA = CommonPoolData(
        BASIC_POOL_DATA,
        USER_POOL_DATA,
    )

    val POOL_DATA = CommonUserPoolData(
        BASIC_POOL_DATA,
        USER_POOL_DATA,
    )

    val NETWORK_FEE = BigDecimal(0.007)
    const val SLIPPAGE_TOLERANCE = 0.5f
    private val SHARE_OF_POOL = BigDecimal("0.34678")
    const val STRATEGIC_BONUS_APY = 0.234

    val LIQUIDITY_DETAILS = LiquidityDetails(
        baseAmount = BigDecimal.ONE,
        targetAmount = BigDecimal.ONE,
        perFirst = BigDecimal.ONE,
        perSecond = BigDecimal.ONE,
        networkFee = BigDecimal.ZERO,
        shareOfPool = SHARE_OF_POOL,
        pairEnabled = true,
        pairPresented = true
    )
}
