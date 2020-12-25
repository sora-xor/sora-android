/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import jp.co.soramitsu.feature_ethereum_impl.util.ContractsApiProvider
import java.math.BigDecimal
import java.math.BigInteger

class EthereumInteractorImpl(
    private val ethereumRepository: EthereumRepository,
    private val didRepository: DidRepository,
    private val healthChecker: HealthChecker
) : EthereumInteractor {

    override fun transferValERC20(to: String, amount: BigDecimal): Completable {
        return didRepository.retrieveMnemonic()
            .flatMap { ethereumRepository.getEthCredentials(it) }
            .flatMap { ethCreds -> ethereumRepository.getValTokenAddress(ethCreds).map { Pair(ethCreds, it) } }
            .flatMapCompletable { ethereumRepository.transferValErc20(to, amount, it.first, it.second) }
    }

    private fun getMinerFeeInitialData(gasLimit: BigInteger): Single<Gas> {
        return didRepository.retrieveMnemonic()
            .flatMap { ethereumRepository.getEthCredentials(it) }
            .flatMap { ethereumRepository.getGasEstimations(gasLimit, it) }
    }

    override fun getMinerFeeInitialDataForTransfer(): Single<Gas> {
        return getMinerFeeInitialData(ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger())
    }

    override fun getMinerFeeInitialDataForTransferWithdraw(): Single<Gas> {
        return getMinerFeeInitialData(ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger() + ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())
    }

    override fun retryWithdrawTransaction(soranetHash: String): Completable {
        return didRepository.getAccountId()
            .flatMapCompletable { accountId ->
                didRepository.retrieveKeypair()
                    .flatMapCompletable { keyPair ->
                        didRepository.retrieveMnemonic()
                            .flatMap {
                                ethereumRepository.getEthCredentials(it)
                            }
                            .flatMapCompletable { ethereumCredentials ->
                                ethereumRepository.getValTokenAddress(ethereumCredentials)
                                    .flatMapCompletable { tokenAddress ->
                                        ethereumRepository.retryWithdraw(ethereumCredentials, soranetHash, accountId, tokenAddress, keyPair)
                                    }
                            }
                    }
            }
    }

    override fun getMinerFeeInitialDataForWithdraw(): Single<Gas> {
        return getMinerFeeInitialData(ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())
    }

    override fun getAddress(): Single<String> {
        return didRepository.retrieveMnemonic()
            .flatMap { ethereumRepository.getEthCredentials(it) }
            .flatMap { ethereumRepository.getEthWalletAddress(it) }
    }

    override fun updateFeeWithCurrentGasLimitAndPrice(gasLimit: BigInteger, gasPrice: BigInteger): Single<BigDecimal> {
        return didRepository.retrieveMnemonic()
            .flatMap { ethereumRepository.setGasLimit(gasLimit) }
            .flatMap { ethereumRepository.setGasPrice(gasPrice) }
    }

    override fun registerEthAccount(): Completable {
        return didRepository.retrieveMnemonic()
            .flatMap { mnemonic ->
                ethereumRepository.getEthCredentials(mnemonic)
                    .flatMap { ethCredentials ->
                        ethereumRepository.getSerializedProof(ethCredentials)
                    }
            }
            .flatMapCompletable { proof ->
                didRepository.getAccountId()
                    .flatMapCompletable { accountId ->
                        didRepository.retrieveKeypair()
                            .flatMapCompletable { keypair ->
                                ethereumRepository.registerEthAccount(accountId, proof, keypair)
                            }
                    }
            }
    }

    override fun startWithdraw(amount: BigDecimal, ethAddress: String, transactionFee: String): Completable {
        return didRepository.getAccountId()
            .flatMapCompletable { accountId ->
                didRepository.retrieveKeypair()
                    .flatMapCompletable { keyPair ->
                        ethereumRepository.startWithdraw(amount, accountId, ethAddress, transactionFee, keyPair)
                    }
            }
    }

    override fun getActualEthRegisterState(): Single<EthRegisterState.State> {
        return ethereumRepository.getActualEthRegisterState()
    }

    override fun startCombinedValErcTransfer(partialAmount: BigDecimal, amount: BigDecimal, ethAddress: String, transactionFee: String): Completable {
        return didRepository.retrieveMnemonic()
            .flatMap { ethereumRepository.getEthCredentials(it) }
            .flatMap { ethCredentials -> ethereumRepository.getEthWalletAddress(ethCredentials).map { Pair(it, ethCredentials) } }
            .flatMapCompletable {
                didRepository.getAccountId()
                    .flatMapCompletable { accountId ->
                        didRepository.retrieveKeypair()
                            .flatMapCompletable { keypair ->
                                ethereumRepository.startCombinedValErcTransfer(partialAmount, amount, accountId, it.first, ethAddress, transactionFee, it.second, keypair)
                            }
                    }
            }
    }

    override fun startCombinedValTransfer(partialAmount: BigDecimal, amount: BigDecimal, peerId: String, peerName: String, transactionFee: BigDecimal, description: String): Completable {
        return didRepository.retrieveKeypair()
            .flatMapCompletable { keyPair ->
                didRepository.retrieveMnemonic()
                    .flatMapCompletable {
                        ethereumRepository.getEthCredentials(it)
                            .flatMap { ethCreds -> ethereumRepository.getValTokenAddress(ethCreds).map { Pair(ethCreds, it) } }
                            .flatMapCompletable { ethereumRepository.startCombinedValTransfer(partialAmount, amount, peerId, peerName, transactionFee, description, it.first, keyPair, it.second) }
                    }
            }
    }

    override fun isBridgeEnabled(): Single<Pair<Boolean, Boolean>> =
        if (healthChecker.ethreumConfigState.value != true)
            Single.just(Pair(first = false, second = true))
        else didRepository.retrieveMnemonic()
            .flatMap {
                ethereumRepository.getEthCredentials(it)
                    .flatMap { ethCreds ->
                        ethereumRepository.isBridgeEnabled(ethCreds).map { br ->
                            Pair(first = true, second = br)
                        }
                    }
            }
}