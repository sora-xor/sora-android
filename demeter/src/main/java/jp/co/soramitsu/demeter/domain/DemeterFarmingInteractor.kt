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
import java.util.Date
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.demeter.data.DemeterFarmingRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.DemeterType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.sora.substrate.substrate.sora2TransactionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

interface DemeterFarmingInteractor {
    suspend fun getFarmedPools(): List<DemeterFarmingPool>?

    fun subscribeFarms(address: String): Flow<String>

    suspend fun getFarmedBasicPools(): List<DemeterFarmingBasicPool>

    suspend fun getStakedFarmedBalanceOfAsset(tokenId: String): BigDecimal

    suspend fun getFarmedPool(ids: StringTriple): DemeterFarmingPool?

    suspend fun getFarmedBasicPool(ids: StringTriple): DemeterFarmingBasicPool?

    suspend fun depositDemeterFarming(
        ids: StringTriple,
        amount: BigDecimal,
        networkFee: BigDecimal,
        /** True only after the user confirmed sending the same action again while one waits. */
        confirmedDuplicate: Boolean = false,
    ): TransactionOutcome

    suspend fun withdrawDemeterFarming(
        ids: StringTriple,
        amount: BigDecimal,
        networkFee: BigDecimal,
        /** True only after the user confirmed sending the same action again while one waits. */
        confirmedDuplicate: Boolean = false,
    ): TransactionOutcome

    suspend fun calcDepositDemeterNetworkFee(ids: StringTriple): BigDecimal

    suspend fun calcWithdrawDemeterNetworkFee(ids: StringTriple): BigDecimal

    suspend fun claimDemeterRewards(
        ids: StringTriple,
        amount: BigDecimal,
        networkFee: BigDecimal,
        /** True only after the user confirmed sending the same action again while one waits. */
        confirmedDuplicate: Boolean = false,
    ): TransactionOutcome

    suspend fun calcClaimDemeterRewards(ids: StringTriple): BigDecimal
}

internal class DemeterFarmingInteractorImpl(
    private val demeterFarmingRepository: DemeterFarmingRepository,
    private val assetRepository: AssetsRepository,
    private val userRepository: UserRepository,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val transactionBuilder: TransactionBuilder,
    private val credentialsRepository: CredentialsRepository,
) : DemeterFarmingInteractor {

    override fun subscribeFarms(address: String): Flow<String> {
        return demeterFarmingRepository.subscribeFarms(address)
    }

    override suspend fun getStakedFarmedBalanceOfAsset(tokenId: String): BigDecimal =
        demeterFarmingRepository.getStakedFarmedAmountOfAsset(
            userRepository.getCurSoraAccount().substrateAddress,
            tokenId,
        )

    override suspend fun getFarmedPool(ids: StringTriple): DemeterFarmingPool? {
        return getFarmedPools()?.firstOrNull {
            it.tokenBase.id == ids.first && it.tokenTarget.id == ids.second && it.tokenReward.id == ids.third
        }
    }

    override suspend fun getFarmedBasicPool(ids: StringTriple): DemeterFarmingBasicPool? {
        return getFarmedBasicPools().firstOrNull {
            it.tokenBase.id == ids.first && it.tokenTarget.id == ids.second && it.tokenReward.id == ids.third
        }
    }

    override suspend fun depositDemeterFarming(
        ids: StringTriple,
        amount: BigDecimal,
        networkFee: BigDecimal,
        confirmedDuplicate: Boolean,
    ): TransactionOutcome {
        val curAcc = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(curAcc)
        val outcome = sora2TransactionOutcome {
            demeterFarmingRepository.depositDemeterFarm(
                curAcc.substrateAddress,
                keypair,
                ids.first,
                ids.second,
                ids.third,
                true,
                amount,
                Sora2SubmissionActivity.demeter(
                    kind = Sora2SubmissionActivity.Kind.DEMETER_STAKE,
                    baseTokenId = ids.first,
                    targetTokenId = ids.second,
                    rewardTokenId = ids.third,
                    amount = amount,
                    fee = networkFee,
                ).copy(duplicateConfirmed = confirmedDuplicate),
            )
        }
        saveToActivityBestEffort(curAcc.substrateAddress) {
            transactionBuilder.buildDemeterStaking(
                outcome.txHash,
                (outcome as? TransactionOutcome.Confirmed)?.blockHash,
                networkFee,
                TransactionStatus.PENDING,
                Date().time,
                amount,
                DemeterType.STAKE,
                assetRepository.getToken(ids.first)!!,
                assetRepository.getToken(ids.second)!!,
                assetRepository.getToken(ids.third)!!,
            )
        }
        return outcome
    }

    override suspend fun calcDepositDemeterNetworkFee(ids: StringTriple): BigDecimal {
        val feeTokenPrecision =
            assetRepository.getToken(SubstrateOptionsProvider.feeAssetId)?.precision ?: OptionsProvider.defaultScale
        val curAcc = userRepository.getCurSoraAccount()

        return demeterFarmingRepository.calcDepositFarmFee(
            curAcc.substrateAddress,
            ids.first,
            ids.second,
            ids.third,
            true,
            feeTokenPrecision
        ) ?: BigDecimal.ZERO
    }

    override suspend fun withdrawDemeterFarming(
        ids: StringTriple,
        amount: BigDecimal,
        networkFee: BigDecimal,
        confirmedDuplicate: Boolean,
    ): TransactionOutcome {
        val curAcc = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(curAcc)
        val outcome = sora2TransactionOutcome {
            demeterFarmingRepository.withdrawDemeterFarm(
                curAcc.substrateAddress,
                keypair,
                ids.first,
                ids.second,
                ids.third,
                true,
                amount,
                Sora2SubmissionActivity.demeter(
                    kind = Sora2SubmissionActivity.Kind.DEMETER_UNSTAKE,
                    baseTokenId = ids.first,
                    targetTokenId = ids.second,
                    rewardTokenId = ids.third,
                    amount = amount,
                    fee = networkFee,
                ).copy(duplicateConfirmed = confirmedDuplicate),
            )
        }
        saveToActivityBestEffort(curAcc.substrateAddress) {
            transactionBuilder.buildDemeterStaking(
                outcome.txHash,
                (outcome as? TransactionOutcome.Confirmed)?.blockHash,
                networkFee,
                TransactionStatus.PENDING,
                Date().time,
                amount,
                DemeterType.UNSTAKE,
                assetRepository.getToken(ids.first)!!,
                assetRepository.getToken(ids.second)!!,
                assetRepository.getToken(ids.third)!!,
            )
        }
        return outcome
    }

    override suspend fun calcWithdrawDemeterNetworkFee(ids: StringTriple): BigDecimal {
        val feeTokenPrecision =
            assetRepository.getToken(SubstrateOptionsProvider.feeAssetId)?.precision ?: OptionsProvider.defaultScale
        val curAcc = userRepository.getCurSoraAccount()

        return demeterFarmingRepository.calcWithdrawFarmFee(
            curAcc.substrateAddress,
            ids.first,
            ids.second,
            ids.third,
            true,
            feeTokenPrecision
        ) ?: BigDecimal.ZERO
    }

    override suspend fun claimDemeterRewards(
        ids: StringTriple,
        amount: BigDecimal,
        networkFee: BigDecimal,
        confirmedDuplicate: Boolean,
    ): TransactionOutcome {
        val curAcc = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(curAcc)
        val outcome = sora2TransactionOutcome {
            demeterFarmingRepository.claimDemeterRewards(
                curAcc.substrateAddress,
                keypair,
                ids.first,
                ids.second,
                ids.third,
                true,
                Sora2SubmissionActivity.demeter(
                    kind = Sora2SubmissionActivity.Kind.DEMETER_CLAIM,
                    baseTokenId = ids.first,
                    targetTokenId = ids.second,
                    rewardTokenId = ids.third,
                    amount = amount,
                    fee = networkFee,
                ).copy(duplicateConfirmed = confirmedDuplicate),
            )
        }
        saveToActivityBestEffort(curAcc.substrateAddress) {
            transactionBuilder.buildDemeterRewards(
                outcome.txHash,
                (outcome as? TransactionOutcome.Confirmed)?.blockHash,
                networkFee,
                TransactionStatus.PENDING,
                Date().time,
                amount,
                DemeterType.REWARD,
                assetRepository.getToken(ids.first)!!,
                assetRepository.getToken(ids.second)!!,
                assetRepository.getToken(ids.third)!!,
            )
        }
        return outcome
    }

    override suspend fun calcClaimDemeterRewards(ids: StringTriple): BigDecimal {
        val feeTokenPrecision =
            assetRepository.getToken(SubstrateOptionsProvider.feeAssetId)?.precision ?: OptionsProvider.defaultScale
        val curAcc = userRepository.getCurSoraAccount()

        return demeterFarmingRepository.calcClaimDemeterRewards(
            curAcc.substrateAddress,
            ids.first,
            ids.second,
            ids.third,
            true,
            feeTokenPrecision
        ) ?: BigDecimal.ZERO
    }

    override suspend fun getFarmedBasicPools(): List<DemeterFarmingBasicPool> =
        demeterFarmingRepository.getFarmedBasicPools().sortedByDescending { it.tvl }

    override suspend fun getFarmedPools(): List<DemeterFarmingPool>? =
        demeterFarmingRepository.getFarmedPools(userRepository.getCurSoraAccount().substrateAddress)

    /** Every outcome is listed in Activity with what it was; its status comes from the journal. */
    private suspend fun saveToActivityBestEffort(
        walletId: String,
        build: suspend () -> Transaction,
    ) {
        try {
            transactionHistoryRepository.saveTransaction(walletId, build())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Activity is rebuilt from the durable journal; it never changes a result.
        }
    }
}
