/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.domain

import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.feature_referral_impl.domain.model.Referral
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class ReferralInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val referralRepository: ReferralRepository,
    private val walletRepository: WalletRepository,
    private val credentialsRepository: CredentialsRepository,
) {

    private var bondFee: BigDecimal? = null

    private var feeToken: Token? = null
    private suspend fun getFeeToken(): Token {
        return feeToken ?: fetchFeeToken().also { feeToken = it }
    }

    suspend fun updateReferrals() {
        val address = userRepository.getCurSoraAccount().substrateAddress
        referralRepository.updateReferralRewards(address)
    }

    suspend fun isLinkOk(link: String): Pair<Boolean, String> {
        val address = link.substringAfterLast(delimiter = "/")
        val myAddress = userRepository.getCurSoraAccount().substrateAddress
        return (credentialsRepository.isAddressOk(address) && (myAddress != address)) to address
    }

    suspend fun getInvitationLink(): String {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return "polkaswap.io/#/referral/$address"
    }

    suspend fun getSetReferrerFee(): BigDecimal {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return referralRepository.getSetReferrerFee(address, getFeeToken())
    }

    suspend fun calcBondFee(): BigDecimal {
        return bondFee ?: fetchBondFee(getFeeToken()).also { bondFee = it }
    }

    private suspend fun fetchBondFee(feeToken: Token): BigDecimal {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return referralRepository.calcBondFee(address, feeToken)
    }

    private suspend fun fetchFeeToken(): Token {
        return walletRepository.getToken(SubstrateOptionsProvider.feeAssetId)!!
    }

    suspend fun observeBond(amount: BigDecimal): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        return referralRepository.observeBond(
            keypair,
            soraAccount.substrateAddress,
            amount,
            getFeeToken()
        )
    }

    suspend fun observeUnbond(amount: BigDecimal): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        return referralRepository.observeUnbond(
            keypair,
            soraAccount.substrateAddress,
            amount,
            getFeeToken()
        )
    }

    suspend fun observeSetReferrer(referrer: String): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        return referralRepository.observeSetReferrer(
            keypair,
            soraAccount.substrateAddress,
            referrer
        )
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
