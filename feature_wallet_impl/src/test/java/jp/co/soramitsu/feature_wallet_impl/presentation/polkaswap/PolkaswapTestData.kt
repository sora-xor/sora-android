/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap

import java.math.BigDecimal
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData

object PolkaswapTestData {

    val TOKEN = Token("token_id", "token name", "token symbol", 18, true, 0)

    private val ASSET_BALANCE = AssetBalance(
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE
    )

    private val ZERO_ASSET_BALANCE = AssetBalance(
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO
    )

    val XOR_ASSET = Asset(
        token = TOKEN.copy(id = OptionsProvider.feeAssetId),
        isDisplaying = true,
        position = 0,
        balance = ASSET_BALANCE
    )

    val XOR_ASSET_ZERO_BALANCE = Asset(
        token = TOKEN.copy(id = OptionsProvider.feeAssetId),
        isDisplaying = true,
        position = 0,
        balance = ZERO_ASSET_BALANCE
    )

    val TEST_ASSET = Asset(
        token = TOKEN,
        isDisplaying = true,
        position = 1,
        balance = ASSET_BALANCE
    )

    val LIQUIDITY_DATA = LiquidityData(
        firstReserves = BigDecimal.ONE,
        secondReserves = BigDecimal.ONE,
        secondPooled = BigDecimal.ONE
    )

    val POOL_DATA = PoolData(
        TEST_ASSET.token,
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
    private val SHARE_OF_POOL = BigDecimal(0.34678)
    val STRATEGIC_BONUS_APY = BigDecimal(0.234)

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