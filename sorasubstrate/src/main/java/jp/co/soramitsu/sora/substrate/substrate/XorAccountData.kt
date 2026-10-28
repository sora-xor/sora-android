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

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import jp.co.soramitsu.common_wallet.domain.BalanceWrapper
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromHex
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.StorageEntry

/**
 * The XOR balance fields of one `System.Account` value. [miscFrozen] is the part of [free] that
 * cannot be transferred and [feeFrozen] the part that cannot pay a network fee; see
 * [jp.co.soramitsu.common_wallet.data.XorBalanceDto].
 */
internal data class XorAccountData(
    val free: BigInteger,
    val reserved: BigInteger,
    val miscFrozen: BigInteger,
    val feeFrozen: BigInteger,
) {
    companion object {
        val EMPTY = XorAccountData(
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
        )
    }
}

/**
 * Decodes the balance part of a `System.Account` storage value with the runtime that is live.
 *
 * SORA mainnet has used two layouts of `pallet_balances::AccountData`:
 *
 * - `free, reserved, frozen, flags` since runtime 124 (6 May 2026). `frozen` is a floor for free
 *   plus reserved (staking, voting and vesting locks).
 * - `free, reserved, misc_frozen, fee_frozen` before that.
 *
 * The field names come from the metadata, so the layout is recognized by the presence of
 * `frozen`. Reading only the old names on the current layout gives zero locks, which showed
 * locked XOR as transferable (audit F4, LIVE-2).
 *
 * `frozen` limits two things differently. A transfer may take free balance down to
 * `frozen - reserved`; the fee withdrawal that comes before it may only take it down to
 * `frozen`. Both limits are kept, so that an account with reserved and frozen XOR is not
 * promised an amount whose fee it cannot pay.
 */
internal fun decodeXorAccountData(
    runtime: RuntimeSnapshot,
    accountStorage: StorageEntry,
    storageHex: String,
): XorAccountData {
    val account = accountStorage.type.value?.fromHex(runtime, storageHex) as? Struct.Instance
    val data = account?.get<Struct.Instance>("data") ?: return XorAccountData.EMPTY
    val free = data.get<BigInteger>("free") ?: BigInteger.ZERO
    val reserved = data.get<BigInteger>("reserved") ?: BigInteger.ZERO
    val frozen = data.get<BigInteger>("frozen")
    if (frozen != null) {
        return XorAccountData(
            free = free,
            reserved = reserved,
            miscFrozen = BalanceWrapper.lockedFreeBalance(
                free = free,
                reserved = reserved,
                frozen = frozen,
            ),
            feeFrozen = BalanceWrapper.feeLockedFreeBalance(free = free, frozen = frozen),
        )
    }
    val miscFrozen = data.get<BigInteger>("miscFrozen") ?: BigInteger.ZERO
    val feeFrozen = data.get<BigInteger>("feeFrozen") ?: BigInteger.ZERO
    return XorAccountData(
        free = free,
        reserved = reserved,
        // The larger of the two is what this app has always held back from transfers there.
        miscFrozen = miscFrozen.max(feeFrozen),
        feeFrozen = feeFrozen,
    )
}
