/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common_wallet.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.frozenTotal
import jp.co.soramitsu.common.domain.lockedFree
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import jp.co.soramitsu.common_wallet.data.XorBalanceDto
import jp.co.soramitsu.core_db.model.AssetLocal

object BalanceWrapper {

    /**
     * The part of [free] that cannot be transferred under the current balances layout
     * (`free, reserved, frozen, flags`). The chain's `frozen` is a floor for free plus reserved,
     * so only what the reserved balance does not already cover locks free balance, and never
     * more than there is: `min(max(frozen - reserved, 0), free)`. This is what
     * pallet_balances' `reducible_balance` leaves untouched (SORA has no existential deposit).
     */
    fun lockedFreeBalance(
        free: BigInteger,
        reserved: BigInteger,
        frozen: BigInteger,
    ): BigInteger = (frozen - reserved).max(BigInteger.ZERO).min(free.max(BigInteger.ZERO))

    /**
     * The part of [free] that cannot pay a network fee under the current balances layout:
     * `min(frozen, free)`. The fee is withdrawn before the call runs, and that withdrawal needs
     * `free - fee >= frozen`; the reserved balance gives no discount there (pallet_balances
     * `Currency::withdraw`, used by SORA's xor-fee pallet). It is therefore never smaller than
     * [lockedFreeBalance], and larger whenever the account has both reserved and frozen XOR.
     */
    fun feeLockedFreeBalance(
        free: BigInteger,
        frozen: BigInteger,
    ): BigInteger = frozen.max(BigInteger.ZERO).min(free.max(BigInteger.ZERO))

    /**
     * What the account can send, never more than the chain allows and never negative.
     *
     * Every transaction pays its fee in XOR first. An account whose whole free balance is held
     * back from fees ([XorBalanceDto.feeFrozen]) cannot pay any fee, so it can send nothing,
     * even where the transfer rule alone would leave an amount. Otherwise it is the free balance
     * minus the part locked against transfers ([XorBalanceDto.miscFrozen]). Whether a given fee
     * fits is a separate question: see [calcPayableAsFee].
     */
    fun calcTransferable(dto: XorBalanceDto): BigInteger {
        val free = dto.free.max(BigInteger.ZERO)
        if (free <= dto.feeFrozen) return BigInteger.ZERO
        return (free - dto.miscFrozen.max(BigInteger.ZERO)).max(BigInteger.ZERO)
    }

    /** The largest network fee the chain lets this account pay right now; never negative. */
    fun calcPayableAsFee(dto: XorBalanceDto): BigInteger =
        (dto.free - dto.feeFrozen.max(BigInteger.ZERO)).max(BigInteger.ZERO)

    /** The part of the free balance that cannot be sent: free minus [calcTransferable]. */
    private fun calcLocked(dto: XorBalanceDto): BigInteger =
        dto.free.max(BigInteger.ZERO) - calcTransferable(dto)

    fun mapLocalBalance(assetLocal: AssetLocal): AssetBalance {
        val free = assetLocal.free.max(BigDecimal.ZERO)
        val transferable = if (free <= assetLocal.feeFrozen) {
            BigDecimal.ZERO
        } else {
            (free - assetLocal.miscFrozen.max(BigDecimal.ZERO)).max(BigDecimal.ZERO)
        }
        return AssetBalance(
            transferable,
            assetLocal.reserved,
            assetLocal.miscFrozen,
            assetLocal.feeFrozen,
            assetLocal.bonded,
            assetLocal.redeemable,
            assetLocal.unbonding,
            assetLocal.free + assetLocal.reserved + assetLocal.bonded,
        )
    }

    fun mapXorBalance(dto: XorBalanceDto, precision: Int): XorAssetBalance {
        return dto.let {
            val locked = calcLocked(dto)
            val transferable = mapBalance(calcTransferable(dto), precision)
            val frozen = mapBalance(it.bonded + it.reserved + locked, precision)
            val total = mapBalance(it.free + it.reserved + it.bonded, precision)
            val bonded = mapBalance(it.bonded, precision)
            val reserved = mapBalance(it.reserved, precision)
            val redeemable = mapBalance(it.redeemable, precision)
            val unbonding = mapBalance(it.unbonding, precision)

            XorAssetBalance(
                transferable,
                frozen,
                total,
                mapBalance(locked, precision),
                bonded,
                reserved,
                redeemable,
                unbonding,
            )
        }
    }

    /**
     * The same breakdown from the row the wallet stored at its last balance update, for when the
     * network cannot be asked. The amounts on the asset page then agree with the balance shown
     * on the home screen, which comes from the same row.
     */
    fun mapStoredXorBalance(balance: AssetBalance): XorAssetBalance = XorAssetBalance(
        transferable = balance.transferable,
        frozen = balance.frozenTotal,
        totalBalance = balance.total,
        locked = balance.lockedFree,
        bonded = balance.bonded,
        reserved = balance.reserved,
        redeemable = balance.redeemable,
        unbonding = balance.unbonding,
    )
}
