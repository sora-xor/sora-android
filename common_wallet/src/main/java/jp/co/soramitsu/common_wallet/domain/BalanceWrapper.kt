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

import java.math.BigInteger
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import jp.co.soramitsu.common_wallet.data.XorBalanceDto
import jp.co.soramitsu.core_db.model.AssetLocal

object BalanceWrapper {

    private fun calcLocked(dto: XorBalanceDto): BigInteger {
        return dto.miscFrozen.max(dto.feeFrozen)
    }

    fun calcTransferable(dto: XorBalanceDto): BigInteger {
        return dto.free - calcLocked(dto)
    }

    private fun calcTransferable(dto: XorBalanceDto, locked: BigInteger): BigInteger {
        return dto.free - locked
    }

    fun mapLocalBalance(assetLocal: AssetLocal): AssetBalance {
        return AssetBalance(
            assetLocal.free - assetLocal.miscFrozen.max(assetLocal.feeFrozen),
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
            val transferable = mapBalance(calcTransferable(dto, locked), precision)
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
}
