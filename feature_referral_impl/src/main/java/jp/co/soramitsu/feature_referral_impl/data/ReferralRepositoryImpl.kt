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

package jp.co.soramitsu.feature_referral_impl.data

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import javax.inject.Inject
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.sora.substrate.blockexplorer.BlockExplorerManager
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.referralBond
import jp.co.soramitsu.sora.substrate.substrate.referralUnbond
import jp.co.soramitsu.sora.substrate.substrate.setReferrer
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.referral.ReferrerReward
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class ReferralRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val extrinsicManager: ExtrinsicManager,
    private val runtimeManager: RuntimeManager,
    private val substrateCalls: SubstrateCalls,
    private val blockExplorerManager: BlockExplorerManager,
) : ReferralRepository {

    override suspend fun updateReferralRewards(address: String) {
        blockExplorerManager.updateReferrerRewards(address)
    }

    override suspend fun observeSetReferrer(
        keypair: Sr25519Keypair,
        from: String,
        referrer: String,
        feeToken: Token
    ): Transaction.ReferralSetReferrer {
        val currentTime = Date().time
        val result = extrinsicManager.submitAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            untilStatus = SubstrateCalls.IN_BLOCK,
        ) {
            setReferrer(referrer)
        }

        return Transaction.ReferralSetReferrer(
            TransactionBase(
                result.txHash,
                result.blockHash,
                BigDecimal.ZERO,
                if (result.success) TransactionStatus.COMMITTED else TransactionStatus.REJECTED,
                currentTime
            ),
            who = referrer,
            myReferrer = true,
            token = feeToken
        )
    }

    override suspend fun calcBondFee(
        from: String,
        token: Token,
    ): BigDecimal? {
        val fee = extrinsicManager.calcFee(
            from = from,
        ) {
            referralBond(BigInteger.ONE)
        }
        return fee?.let {
            mapBalance(it, token.precision)
        }
    }

    override suspend fun observeUnbond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token,
        fee: BigDecimal,
    ): Transaction.ReferralUnbond {
        val mapped = mapBalance(amount, token.precision)
        val currentTime = Date().time
        val result = extrinsicManager.submitAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            untilStatus = SubstrateCalls.IN_BLOCK,
        ) {
            referralUnbond(mapped)
        }

        return Transaction.ReferralUnbond(
            TransactionBase(
                result.txHash,
                result.blockHash,
                fee,
                if (result.success) TransactionStatus.COMMITTED else TransactionStatus.REJECTED,
                currentTime
            ),
            amount,
            token
        )
    }

    override suspend fun observeBond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token,
        fee: BigDecimal,
    ): Transaction.ReferralBond {
        val mapped = mapBalance(amount, token.precision)
        val currentTime = Date().time
        val result = extrinsicManager.submitAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            untilStatus = SubstrateCalls.IN_BLOCK,
        ) {
            referralBond(mapped)
        }

        return Transaction.ReferralBond(
            TransactionBase(
                result.txHash,
                result.blockHash,
                fee,
                if (result.success) TransactionStatus.COMMITTED else TransactionStatus.REJECTED,
                currentTime
            ),
            amount,
            token
        )
    }

    override fun observerReferrals(from: String): Flow<String> = flow {
        val runtime = runtimeManager.getRuntimeSnapshot()
        val storage = runtime.metadata.module(Pallete.Referrals.palletName)
            .storage(Storage.REFERRALS.storageName)
        val storageKey = storage.storageKey(runtime, from.toAccountId())
        val resultFlow = substrateCalls.observeStorage(storageKey)
        emitAll(resultFlow)
    }

    override fun observeMyReferrer(from: String): Flow<String> = flow {
        val runtime = runtimeManager.getRuntimeSnapshot()
        val storage = runtime.metadata.module(Pallete.Referrals.palletName)
            .storage(Storage.REFERRERS.storageName)
        val storageKey = storage.storageKey(runtime, from.toAccountId())
        val resultFlow = substrateCalls.observeStorage(storageKey).map { hex ->
            if (hex.isNotEmpty()) {
                runtimeManager.toSoraAddressOrNull(
                    storage.type.value?.fromHex(runtime, hex)?.safeCast<ByteArray>()
                ).orEmpty()
            } else {
                hex
            }
        }
        emitAll(resultFlow)
    }

    override fun observeReferrerBalance(from: String, feeToken: Token): Flow<BigDecimal?> = substrateCalls.observeReferrerBalance(from).map { b ->
        b?.let { mapBalance(it, feeToken.precision) }
    }

    override suspend fun getSetReferrerFee(from: String, feeToken: Token): BigDecimal? {
        val fee = extrinsicManager.calcFee(
            from = from,
        ) {
            setReferrer(from)
        }
        return fee?.let {
            mapBalance(it, feeToken.precision)
        }
    }

    override fun getReferralRewards(): Flow<List<ReferrerReward>> {
        return db.referralsDao().getReferrals().map { list ->
            list.map {
                ReferrerReward(it.address, it.amount)
            }
        }
    }
}
