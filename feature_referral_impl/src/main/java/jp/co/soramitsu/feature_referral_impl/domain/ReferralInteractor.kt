/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
