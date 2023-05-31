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

package jp.co.soramitsu.feature_referral_impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.feature_referral_impl.domain.model.Referral
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
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
    private var bondFee: BigDecimal? = null

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
        return bondFee ?: fetchBondFee(getFeeToken()).also { bondFee = it }
    }

    private suspend fun fetchBondFee(feeToken: Token): BigDecimal {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return referralRepository.calcBondFee(address, feeToken) ?: BigDecimal.ZERO
    }

    private suspend fun fetchFeeToken(): Token {
        return assetsRepository.getToken(SubstrateOptionsProvider.feeAssetId)!!
    }

    suspend fun observeBond(amount: BigDecimal): String {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val result = referralRepository.observeBond(
            keypair,
            soraAccount.substrateAddress,
            amount,
            getFeeToken()
        )

        transactionHistoryRepository.saveTransaction(result)

        return result.base.txHash
    }

    suspend fun observeUnbond(amount: BigDecimal): String {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val result = referralRepository.observeUnbond(
            keypair,
            soraAccount.substrateAddress,
            amount,
            getFeeToken()
        )

        transactionHistoryRepository.saveTransaction(result)

        return result.base.txHash
    }

    suspend fun observeSetReferrer(referrer: String): String {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val feeToken =
            requireNotNull(assetsRepository.getToken(SubstrateOptionsProvider.feeAssetId))
        val result = referralRepository.observeSetReferrer(
            keypair,
            soraAccount.substrateAddress,
            referrer,
            feeToken
        )

        transactionHistoryRepository.saveTransaction(result)

        return result.base.txHash
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
