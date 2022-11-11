/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.data

import androidx.room.withTransaction
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.referralBond
import jp.co.soramitsu.sora.substrate.substrate.referralUnbond
import jp.co.soramitsu.sora.substrate.substrate.setReferrer
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.SoraWalletBlockExplorerInfo
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.referral.ReferrerReward
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class ReferralRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val extrinsicManager: ExtrinsicManager,
    private val runtimeManager: RuntimeManager,
    private val substrateCalls: SubstrateCalls,
    private val soraWalletBlockExplorerInfo: SoraWalletBlockExplorerInfo,
) : ReferralRepository {

    private val referralRewardCaseName =
        if (BuildUtils.isFlavors(Flavor.PROD)) "0" else "1"

    override suspend fun updateReferralRewards(address: String) {
        db.withTransaction {
            db.referralsDao().clearTable()
            runCatching {
                soraWalletBlockExplorerInfo.getReferrerRewards(
                    address,
                    referralRewardCaseName
                ).rewards.map {
                    ReferralLocal(it.referral, it.amount)
                }
            }
                .onSuccess {
                    db.referralsDao().insertReferrals(it)
                }
        }
    }

    override suspend fun observeSetReferrer(
        keypair: Sr25519Keypair,
        from: String,
        referrer: String
    ): Boolean {
        return extrinsicManager.submitAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            untilStatus = SubstrateCalls.IN_BLOCK,
        ) {
            setReferrer(referrer)
        }.success
    }

    override suspend fun calcBondFee(
        from: String,
        token: Token,
    ): BigDecimal {
        val fee = extrinsicManager.calcFee(
            from = from,
        ) {
            referralBond(BigInteger.ONE)
        }
        return mapBalance(fee, token.precision)
    }

    override suspend fun observeUnbond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token,
    ): Boolean {
        val mapped = mapBalance(amount, token.precision)
        return extrinsicManager.submitAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            untilStatus = SubstrateCalls.IN_BLOCK,
        ) {
            referralUnbond(mapped)
        }.success
    }

    override suspend fun observeBond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token
    ): Boolean {
        val mapped = mapBalance(amount, token.precision)
        return extrinsicManager.submitAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            untilStatus = SubstrateCalls.IN_BLOCK,
        ) {
            referralBond(mapped)
        }.success
    }

    override fun observerReferrals(from: String): Flow<String> {
        val runtime = runtimeManager.getRuntimeSnapshot()
        val storage = runtime.metadata.module(Pallete.Referrals.palletName)
            .storage(Storage.REFERRALS.storageName)
        val storageKey = storage.storageKey(runtime, from.toAccountId())
        return substrateCalls.observeStorage(storageKey)
    }

    override fun observeMyReferrer(from: String): Flow<String> {
        val runtime = runtimeManager.getRuntimeSnapshot()
        val storage = runtime.metadata.module(Pallete.Referrals.palletName)
            .storage(Storage.REFERRERS.storageName)
        val storageKey = storage.storageKey(runtime, from.toAccountId())
        return substrateCalls.observeStorage(storageKey).map { hex ->
            if (hex.isNotEmpty()) {
                runtimeManager.toSoraAddressOrNull(
                    storage.type.value?.fromHex(runtime, hex)?.safeCast<ByteArray>()
                ).orEmpty()
            } else {
                hex
            }
        }
    }

    override fun observeReferrerBalance(from: String, feeToken: Token): Flow<BigDecimal?> {
        val runtime = runtimeManager.getRuntimeSnapshot()
        val storage = runtime.metadata.module(Pallete.Referrals.palletName)
            .storage(Storage.REFERRER_BALANCE.storageName)
        val storageKey = storage.storageKey(runtime, from.toAccountId())
        return substrateCalls.observeStorage(storageKey).map { hex ->
            if (hex.isNotEmpty()) {
                runCatching {
                    storage.type.value?.fromHex(runtime, hex)?.safeCast<BigInteger>()?.let { b ->
                        mapBalance(b, feeToken.precision)
                    }
                }.getOrNull()
            } else {
                null
            }
        }
    }

    override suspend fun getSetReferrerFee(from: String, feeToken: Token): BigDecimal {
        val fee = extrinsicManager.calcFee(
            from = from,
        ) {
            setReferrer(from)
        }
        return mapBalance(fee, feeToken.precision)
    }

    override fun getReferralRewards(): Flow<List<ReferrerReward>> {
        return db.referralsDao().getReferrals().map { list ->
            list.map {
                ReferrerReward(it.address, it.amount)
            }
        }
    }
}
