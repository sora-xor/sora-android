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

package jp.co.soramitsu.feature_referral_impl.data

import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.feature_referral_api.data.ReferralReward
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.referralBond
import jp.co.soramitsu.sora.substrate.substrate.referralUnbond
import jp.co.soramitsu.sora.substrate.substrate.setReferrer
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromHex
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
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
        feeToken: Token,
        activity: Sora2SubmissionActivity?,
    ): ExtrinsicSubmitStatus = extrinsicManager.submitAndWaitExtrinsic(
        from = from,
        keypair = keypair,
        // `inBlock` is not canonical finality and can be reorged. The shared
        // extrinsic manager resolves execution events from the returned block,
        // so referral state must wait for the finalized notification just like
        // every other production SORA2 mutation.
        untilStatus = SubstrateCalls.FINALIZED,
        activity = activity,
    ) {
        setReferrer(referrer)
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
        activity: Sora2SubmissionActivity?,
    ): ExtrinsicSubmitStatus {
        val mapped = mapBalance(amount, token.precision)
        return extrinsicManager.submitAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            untilStatus = SubstrateCalls.FINALIZED,
            activity = activity,
        ) {
            referralUnbond(mapped)
        }
    }

    override suspend fun observeBond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token,
        fee: BigDecimal,
        activity: Sora2SubmissionActivity?,
    ): ExtrinsicSubmitStatus {
        val mapped = mapBalance(amount, token.precision)
        return extrinsicManager.submitAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            untilStatus = SubstrateCalls.FINALIZED,
            activity = activity,
        ) {
            referralBond(mapped)
        }
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

    override fun observeReferrerBalance(from: String, feeToken: Token): Flow<BigDecimal?> =
        substrateCalls.observeReferrerBalance(from).map { b ->
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

    override fun getReferralRewards(): Flow<List<ReferralReward>> {
        return db.referralsDao().getReferrals().map { list ->
            list.map {
                ReferralReward(it.address, it.amount)
            }
        }
    }
}
