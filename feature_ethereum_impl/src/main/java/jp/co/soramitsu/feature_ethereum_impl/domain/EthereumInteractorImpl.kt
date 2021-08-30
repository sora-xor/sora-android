/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.domain

import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import jp.co.soramitsu.feature_ethereum_impl.util.ContractsApiProvider
import java.math.BigDecimal
import java.math.BigInteger

class EthereumInteractorImpl(
    private val ethereumRepository: EthereumRepository,
    private val credentialsRepository: CredentialsRepository,
    private val healthChecker: HealthChecker
) : EthereumInteractor {

    override fun transferValERC20(to: String, amount: BigDecimal) {
//        return credentialsRepository.retrieveMnemonic()
//            .flatMap { ethereumRepository.getEthCredentials(it) }
//            .flatMap { ethCreds -> ethereumRepository.getValTokenAddress(ethCreds).map { Pair(ethCreds, it) } }
//            .flatMapCompletable { ethereumRepository.transferValErc20(to, amount, it.first, it.second) }
    }

    private fun getMinerFeeInitialData(gasLimit: BigInteger): Gas {
        return Gas(BigInteger.ZERO, BigInteger.ZERO, emptyList())
//        return credentialsRepository.retrieveMnemonic()
//            .flatMap { ethereumRepository.getEthCredentials(it) }
//            .flatMap { ethereumRepository.getGasEstimations(gasLimit, it) }
    }

    override fun getMinerFeeInitialDataForTransfer(): Gas {
        return getMinerFeeInitialData(ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger())
    }

    override fun getMinerFeeInitialDataForTransferWithdraw(): Gas {
        return getMinerFeeInitialData(ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger() + ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())
    }

    override fun retryWithdrawTransaction(soranetHash: String) {
//        return credentialsRepository.getAddress()
//            .flatMapCompletable { accountId ->
//                credentialsRepository.retrieveKeyPair()
//                    .flatMapCompletable { keyPair ->
//                        credentialsRepository.retrieveMnemonic()
//                            .flatMap {
//                                ethereumRepository.getEthCredentials(it)
//                            }
//                            .flatMapCompletable { ethereumCredentials ->
//                                ethereumRepository.getValTokenAddress(ethereumCredentials)
//                                    .flatMapCompletable { tokenAddress ->
//                                        Completable.complete()
//                                    }
//                            }
//                    }
//            }
    }

    override fun getMinerFeeInitialDataForWithdraw(): Gas {
        return getMinerFeeInitialData(ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())
    }

    override fun getAddress(): String {
        return ""
//        return credentialsRepository.retrieveMnemonic()
//            .flatMap { ethereumRepository.getEthCredentials(it) }
//            .flatMap { ethereumRepository.getEthWalletAddress(it) }
    }

    override fun updateFeeWithCurrentGasLimitAndPrice(
        gasLimit: BigInteger,
        gasPrice: BigInteger
    ): BigDecimal {
        return BigDecimal.ZERO
//        return credentialsRepository.retrieveMnemonic()
//            .flatMap { ethereumRepository.setGasLimit(gasLimit) }
//            .flatMap { ethereumRepository.setGasPrice(gasPrice) }
    }

    override fun startWithdraw(amount: BigDecimal, ethAddress: String, transactionFee: String) {
//        return credentialsRepository.getAddress()
//            .flatMapCompletable { accountId ->
//                credentialsRepository.retrieveKeyPair()
//                    .flatMapCompletable { keyPair ->
//                        ethereumRepository.startWithdraw(amount, accountId, ethAddress, transactionFee, KeyPair(null, null))
//                    }
//            }
    }

    override fun getActualEthRegisterState(): EthRegisterState.State {
        return ethereumRepository.getActualEthRegisterState()
    }

    override fun startCombinedValErcTransfer(
        partialAmount: BigDecimal,
        amount: BigDecimal,
        ethAddress: String,
        transactionFee: String
    ) {
//        return credentialsRepository.retrieveMnemonic()
//            .flatMap { ethereumRepository.getEthCredentials(it) }
//            .flatMap { ethCredentials -> ethereumRepository.getEthWalletAddress(ethCredentials).map { Pair(it, ethCredentials) } }
//            .flatMapCompletable {
//                credentialsRepository.getAddress()
//                    .flatMapCompletable { accountId ->
//                        credentialsRepository.retrieveKeyPair()
//                            .flatMapCompletable { keypair ->
//                                ethereumRepository.startCombinedValErcTransfer(partialAmount, amount, accountId, it.first, ethAddress, transactionFee, it.second, KeyPair(null, null))
//                            }
//                    }
//            }
    }

    override fun startCombinedValTransfer(
        partialAmount: BigDecimal,
        amount: BigDecimal,
        peerId: String,
        peerFullName: String,
        transactionFee: BigDecimal,
        description: String
    ) {
//        return credentialsRepository.retrieveKeyPair()
//            .flatMapCompletable { keyPair ->
//                credentialsRepository.retrieveMnemonic()
//                    .flatMapCompletable {
//                        ethereumRepository.getEthCredentials(it)
//                            .flatMap { ethCreds -> ethereumRepository.getValTokenAddress(ethCreds).map { Pair(ethCreds, it) } }
//                            .flatMapCompletable { ethereumRepository.startCombinedValTransfer(partialAmount, amount, peerId, peerFullName, transactionFee, description, it.first, KeyPair(null, null), it.second) }
//                    }
//            }
    }

    override fun isBridgeEnabled(): Pair<Boolean, Boolean> =
        true to true
//        if (healthChecker.ethreumConfigState.value != true)
//            Single.just(Pair(first = false, second = true))
//        else credentialsRepository.retrieveMnemonic()
//            .flatMap {
//                ethereumRepository.getEthCredentials(it)
//                    .flatMap { ethCreds ->
//                        ethereumRepository.isBridgeEnabled(ethCreds).map { br ->
//                            Pair(first = true, second = br)
//                        }
//                    }
//            }
}
