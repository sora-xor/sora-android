/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.test_data

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.PoolData

object PolkaswapTestData {

    val XOR_ASSET = TestAssets.xorAsset(BigDecimal.ONE)

    val XOR_ASSET_ZERO_BALANCE = TestAssets.xorAsset(BigDecimal.ZERO)

    val TEST_ASSET = TestAssets.valAsset(BigDecimal.ONE)

    val LIQUIDITY_DATA = LiquidityData(
        firstReserves = BigDecimal.ONE,
        secondReserves = BigDecimal.ONE,
        secondPooled = BigDecimal.ONE
    )

    val POOL_DATA = PoolData(
        TEST_ASSET.token,
        XOR_ASSET.token,
        BigDecimal.ONE,
        BigDecimal.TEN,
        BigDecimal.ONE,
        BigDecimal.TEN,
        1.0,
        10.0,
        BigDecimal.TEN,
        BigDecimal.TEN,
        true,
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
