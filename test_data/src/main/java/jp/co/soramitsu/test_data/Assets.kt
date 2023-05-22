/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.test_data

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance

object TestAssets {

    fun xorAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.xorToken,
        favorite = true,
        visibility = true,
        position = 0,
        balance = balance(balance),
    )

    fun valAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.valToken,
        favorite = true,
        visibility = true,
        position = 1,
        balance = balance(balance),
    )

    fun pswapAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.pswapToken,
        favorite = true,
        visibility = true,
        position = 2,
        balance = balance(balance),
    )

    fun ethAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.ethToken,
        favorite = true,
        visibility = true,
        position = 3,
        balance = balance(balance),
    )

    fun xstAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.xstusdToken,
        favorite = true,
        visibility = true,
        position = 4,
        balance = balance(balance),
    )

    fun daiAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.daiToken,
        favorite = true,
        visibility = true,
        position = 5,
        balance = balance(balance),
    )

    fun balance(balance: BigDecimal) = AssetBalance(
        transferable = balance,
        reserved = balance,
        miscFrozen = balance,
        feeFrozen = balance,
        bonded = balance,
        redeemable = balance,
        unbonding = balance,
    )
}
