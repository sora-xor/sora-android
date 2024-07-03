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

package jp.co.soramitsu.feature_referral_api.data

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.models.ReferralReward
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.flow.Flow

interface ReferralRepository {

    suspend fun updateReferralRewards(address: String)

    fun getReferralRewards(): Flow<List<ReferralReward>>

    suspend fun getSetReferrerFee(from: String, feeToken: Token): BigDecimal?

    fun observeMyReferrer(from: String): Flow<String>

    fun observerReferrals(from: String): Flow<String>

    fun observeReferrerBalance(from: String, feeToken: Token): Flow<BigDecimal?>

    suspend fun observeSetReferrer(
        keypair: Sr25519Keypair,
        from: String,
        referrer: String,
        feeToken: Token
    ): Transaction.ReferralSetReferrer

    suspend fun observeUnbond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token,
        fee: BigDecimal,
    ): Transaction.ReferralUnbond

    suspend fun observeBond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token,
        fee: BigDecimal,
    ): Transaction.ReferralBond

    suspend fun calcBondFee(
        from: String,
        token: Token,
    ): BigDecimal?
}
