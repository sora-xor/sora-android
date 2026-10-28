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

    fun xstusdAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
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

    fun xstxauAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.xstxau,
        favorite = true,
        visibility = true,
        position = 6,
        balance = balance(balance),
    )

    fun xstAsset(balance: BigDecimal = BigDecimal.ZERO) = Asset(
        token = TestTokens.xstToken,
        favorite = true,
        visibility = true,
        position = 7,
        balance = balance(balance),
    )

    /** A plain balance: all of it free and transferable, nothing reserved, bonded or locked. */
    fun balance(balance: BigDecimal) = AssetBalance(
        transferable = balance,
        reserved = BigDecimal.ZERO,
        miscFrozen = BigDecimal.ZERO,
        feeFrozen = BigDecimal.ZERO,
        bonded = BigDecimal.ZERO,
        redeemable = BigDecimal.ZERO,
        unbonding = BigDecimal.ZERO,
        total = balance,
    )

    /**
     * A balance as the wallet stores it for an account with locked XOR: [transferable] can be
     * sent, [locked] more is free on the chain and held back from transfers, and [feeLocked] is
     * held back from fees (at least [locked]; more when the account also has reserved XOR).
     */
    fun lockedBalance(
        transferable: BigDecimal,
        locked: BigDecimal,
        reserved: BigDecimal = BigDecimal.ZERO,
        feeLocked: BigDecimal = locked,
    ) = AssetBalance(
        transferable = transferable,
        reserved = reserved,
        miscFrozen = locked,
        feeFrozen = feeLocked,
        bonded = BigDecimal.ZERO,
        redeemable = BigDecimal.ZERO,
        unbonding = BigDecimal.ZERO,
        total = transferable + locked + reserved,
    )
}
