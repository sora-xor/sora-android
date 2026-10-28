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

package jp.co.soramitsu.common_wallet.data

import java.math.BigInteger

/**
 * XOR balance of one account, in the smallest unit.
 *
 * [miscFrozen] and [feeFrozen] say how much of [free] is held back, each for one purpose:
 *
 * - [miscFrozen]: the part that cannot be transferred.
 * - [feeFrozen]: the part that cannot pay a network fee.
 *
 * Under the balances layout mainnet has used since runtime 124 (`free, reserved, frozen, flags`)
 * they are `min(max(frozen - reserved, 0), free)` and `min(frozen, free)`; see
 * `BalanceWrapper.lockedFreeBalance` and `BalanceWrapper.feeLockedFreeBalance`. The second is
 * never the smaller one. Under the older layout they are `max(misc_frozen, fee_frozen)`, the
 * lock this app has always applied to transfers there, and the chain's `fee_frozen`.
 *
 * `BalanceWrapper.calcTransferable` and `BalanceWrapper.calcPayableAsFee` turn them into the
 * amounts that screens and checks use.
 */
data class XorBalanceDto(
    val free: BigInteger,
    val reserved: BigInteger,
    val miscFrozen: BigInteger,
    val feeFrozen: BigInteger,
    val bonded: BigInteger,
    val redeemable: BigInteger,
    val unbonding: BigInteger
)
