/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.test_data

import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import java.math.BigDecimal

object TestAssets {

    fun xorAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.xorToken,
        isDisplaying = true,
        position = 0,
        balance = AssetBalance(
            transferable = balance,
            reserved = balance,
            miscFrozen = balance,
            feeFrozen = balance,
            bonded = balance,
            redeemable = balance,
            unbonding = balance,
        )
    )

    fun valAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.valToken,
        isDisplaying = true,
        position = 1,
        balance = AssetBalance(
            transferable = balance,
            reserved = balance,
            miscFrozen = balance,
            feeFrozen = balance,
            bonded = balance,
            redeemable = balance,
            unbonding = balance,
        )
    )

    fun pswapAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.pswapToken,
        isDisplaying = true,
        position = 2,
        balance = AssetBalance(
            transferable = balance,
            reserved = balance,
            miscFrozen = balance,
            feeFrozen = balance,
            bonded = balance,
            redeemable = balance,
            unbonding = balance,
        )
    )

    fun ethAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.ethToken,
        isDisplaying = true,
        position = 3,
        balance = AssetBalance(
            transferable = balance,
            reserved = balance,
            miscFrozen = balance,
            feeFrozen = balance,
            bonded = balance,
            redeemable = balance,
            unbonding = balance,
        )
    )

    fun xstAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.xstToken,
        isDisplaying = true,
        position = 4,
        balance = AssetBalance(
            transferable = balance,
            reserved = balance,
            miscFrozen = balance,
            feeFrozen = balance,
            bonded = balance,
            redeemable = balance,
            unbonding = balance,
        )
    )

    fun daiAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.daiToken,
        isDisplaying = true,
        position = 5,
        balance = AssetBalance(
            transferable = balance,
            reserved = balance,
            miscFrozen = balance,
            feeFrozen = balance,
            bonded = balance,
            redeemable = balance,
            unbonding = balance,
        )
    )
}
