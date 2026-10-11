/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import jp.co.soramitsu.common_wallet.domain.BalanceWrapper
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromHex
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.StorageEntry

/**
 * The XOR balance fields of one `System.Account` value. [miscFrozen] and [feeFrozen] are the part
 * of [free] that is locked; see [jp.co.soramitsu.common_wallet.data.XorBalanceDto].
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
        val locked = BalanceWrapper.lockedFreeBalance(
            free = free,
            reserved = reserved,
            frozen = frozen,
        )
        return XorAccountData(free, reserved, miscFrozen = locked, feeFrozen = locked)
    }
    return XorAccountData(
        free = free,
        reserved = reserved,
        miscFrozen = data.get<BigInteger>("miscFrozen") ?: BigInteger.ZERO,
        feeFrozen = data.get<BigInteger>("feeFrozen") ?: BigInteger.ZERO,
    )
}
