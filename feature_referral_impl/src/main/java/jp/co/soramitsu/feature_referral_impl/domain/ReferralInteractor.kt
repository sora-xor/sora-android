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

package jp.co.soramitsu.feature_referral_impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import javax.inject.Inject
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.feature_referral_impl.domain.model.Referral
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.sora.substrate.substrate.sora2TransactionOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class ReferralInteractor @Inject constructor(
    private val assetsRepository: AssetsRepository,
    private val userRepository: UserRepository,
    private val referralRepository: ReferralRepository,
    private val walletRepository: WalletRepository,
    private val credentialsRepository: CredentialsRepository,
    private val runtimeManager: RuntimeManager,
    private val transactionHistoryRepository: TransactionHistoryRepository,
) {

    private val referralLinkTemplate = "polkaswap.io/#/referral/"
    private var feeToken: Token? = null
    private suspend fun getFeeToken(): Token {
        return feeToken ?: fetchFeeToken().also { feeToken = it }
    }

    suspend fun updateReferrals() {
        val address = userRepository.getCurSoraAccount().substrateAddress
        referralRepository.updateReferralRewards(address)
    }

    suspend fun isLinkOrAddressOk(input: String): Pair<Boolean, String> {
        val address = if (input.contains(referralLinkTemplate)) {
            input.substringAfterLast(delimiter = "/")
        } else {
            input
        }
        val myAddress = userRepository.getCurSoraAccount().substrateAddress
        return (runtimeManager.isAddressOk(address) && (myAddress != address)) to address
    }

    suspend fun getInvitationLink(): String {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return "$referralLinkTemplate$address"
    }

    suspend fun getSetReferrerFee(): BigDecimal {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return referralRepository.getSetReferrerFee(address, getFeeToken()) ?: BigDecimal.ZERO
    }

    suspend fun calcBondFee(): BigDecimal {
        // A quote belongs to the current account and runtime; an unavailable fee must be retryable.
        return fetchBondFee(getFeeToken())
    }

    private suspend fun fetchBondFee(feeToken: Token): BigDecimal {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return referralRepository.calcBondFee(address, feeToken) ?: BigDecimal.ZERO
    }

    private suspend fun fetchFeeToken(): Token {
        return assetsRepository.getToken(SubstrateOptionsProvider.feeAssetId)!!
    }

    /**
     * Bonds XOR for invitations and reports what is known about the transaction. Every outcome
     * is listed in Activity as a referral bond; its status comes from the durable journal.
     */
    suspend fun observeBond(
        amount: BigDecimal,
        confirmedDuplicate: Boolean = false,
    ): TransactionOutcome {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val feeToken = getFeeToken()
        val fee = calcBondFee()
        val outcome = sora2TransactionOutcome {
            referralRepository.observeBond(
                keypair,
                soraAccount.substrateAddress,
                amount,
                feeToken,
                fee,
                Sora2SubmissionActivity.referralBond(
                    bond = true,
                    tokenId = feeToken.id,
                    amount = amount,
                    fee = fee,
                ).copy(duplicateConfirmed = confirmedDuplicate),
            )
        }
        saveToActivityBestEffort(soraAccount.substrateAddress) {
            Transaction.ReferralBond(activityBase(outcome, fee), amount, feeToken)
        }
        return outcome
    }

    suspend fun observeUnbond(
        amount: BigDecimal,
        confirmedDuplicate: Boolean = false,
    ): TransactionOutcome {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val feeToken = getFeeToken()
        val fee = calcBondFee()
        val outcome = sora2TransactionOutcome {
            referralRepository.observeUnbond(
                keypair,
                soraAccount.substrateAddress,
                amount,
                feeToken,
                fee,
                Sora2SubmissionActivity.referralBond(
                    bond = false,
                    tokenId = feeToken.id,
                    amount = amount,
                    fee = fee,
                ).copy(duplicateConfirmed = confirmedDuplicate),
            )
        }
        saveToActivityBestEffort(soraAccount.substrateAddress) {
            Transaction.ReferralUnbond(activityBase(outcome, fee), amount, feeToken)
        }
        return outcome
    }

    suspend fun observeSetReferrer(
        referrer: String,
        confirmedDuplicate: Boolean = false,
    ): TransactionOutcome {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val feeToken =
            requireNotNull(assetsRepository.getToken(SubstrateOptionsProvider.feeAssetId))
        val outcome = sora2TransactionOutcome {
            referralRepository.observeSetReferrer(
                keypair,
                soraAccount.substrateAddress,
                referrer,
                feeToken,
                Sora2SubmissionActivity.referralSetReferrer(
                    tokenId = feeToken.id,
                    referrer = referrer,
                ).copy(duplicateConfirmed = confirmedDuplicate),
            )
        }
        saveToActivityBestEffort(soraAccount.substrateAddress) {
            Transaction.ReferralSetReferrer(
                activityBase(outcome, BigDecimal.ZERO),
                who = referrer,
                myReferrer = true,
                token = feeToken,
            )
        }
        return outcome
    }

    private fun activityBase(outcome: TransactionOutcome, fee: BigDecimal) = TransactionBase(
        txHash = outcome.txHash,
        blockHash = (outcome as? TransactionOutcome.Confirmed)?.blockHash,
        fee = fee,
        status = TransactionStatus.PENDING,
        timestamp = Date().time,
    )

    private fun saveToActivityBestEffort(walletId: String, build: () -> Transaction) {
        try {
            transactionHistoryRepository.saveTransaction(walletId, build())
        } catch (_: Exception) {
            // Activity is rebuilt from the durable journal; it never changes a result.
        }
    }

    fun observeReferrerBalance(): Flow<BigDecimal?> = flow {
        val address = userRepository.getCurSoraAccount().substrateAddress
        emitAll(referralRepository.observeReferrerBalance(address, getFeeToken()))
    }

    fun observeReferrals(): Flow<String> = flow {
        val address = userRepository.getCurSoraAccount().substrateAddress
        emitAll(referralRepository.observerReferrals(address))
    }

    fun observeMyReferrer(): Flow<String> = flow {
        val address = userRepository.getCurSoraAccount().substrateAddress
        emitAll(referralRepository.observeMyReferrer(address))
    }

    fun getReferrals(): Flow<List<Referral>> {
        return referralRepository.getReferralRewards().map { list ->
            list.map {
                Referral(
                    it.referral.truncateUserAddress(),
                    mapBalance(BigInteger(it.amount), 18)
                )
            }
        }
    }
}
