/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap

import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import java.math.BigDecimal

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
        BigDecimal.ONE,
        10.0,
        BigDecimal.TEN,
        BigDecimal.TEN,
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