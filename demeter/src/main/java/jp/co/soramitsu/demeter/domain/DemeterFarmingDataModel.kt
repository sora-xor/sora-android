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

package jp.co.soramitsu.demeter.domain

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.StringTriple

data class DemeterFarmingPool(
    val tokenBase: Token,
    val tokenTarget: Token,
    val tokenReward: Token,
    val apr: Double,
    val amount: BigDecimal,
    val amountReward: BigDecimal,
)

data class DemeterFarmingBasicPool(
    val tokenBase: Token,
    val tokenTarget: Token,
    val tokenReward: Token,
    val apr: Double,
    val tvl: BigDecimal,
    val fee: Double,
)

fun DemeterFarmingBasicPool.isFilterMatch(filter: String): Boolean {
    val t1 = tokenBase.name.lowercase().contains(filter.lowercase()) ||
        tokenBase.symbol.lowercase().contains(filter.lowercase()) ||
        tokenBase.id.lowercase().contains(filter.lowercase())
    val t2 = tokenTarget.name.lowercase().contains(filter.lowercase()) ||
        tokenTarget.symbol.lowercase().contains(filter.lowercase()) ||
        tokenTarget.id.lowercase().contains(filter.lowercase())
    val t3 = tokenReward.name.lowercase().contains(filter.lowercase()) ||
        tokenReward.symbol.lowercase().contains(filter.lowercase()) ||
        tokenReward.id.lowercase().contains(filter.lowercase())
    return t1 || t2 || t3
}

fun DemeterFarmingBasicPool.ids() = StringTriple(this.tokenBase.id, this.tokenTarget.id, this.tokenReward.id)
