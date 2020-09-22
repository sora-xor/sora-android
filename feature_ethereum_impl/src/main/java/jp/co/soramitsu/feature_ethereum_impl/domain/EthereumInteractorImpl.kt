package jp.co.soramitsu.feature_ethereum_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import java.math.BigDecimal
import java.math.BigInteger

class EthereumInteractorImpl(
    private val ethereumRepository: EthereumRepository,
    private val didRepository: DidRepository
) : EthereumInteractor {

    override fun transferXorERC20(to: String, amount: BigDecimal): Completable {
        return didRepository.retrieveMnemonic()
            .flatMap { ethereumRepository.getEthCredentials(it) }
            .flatMap { ethCreds -> ethereumRepository.getXorTokenAddress(ethCreds).map { Pair(ethCreds, it) } }
            .flatMapCompletable { ethereumRepository.transferXorErc20(to, amount, it.first, it.second) }
    }

    override fun getMinerFeeInitialData(): Single<Gas> {
        return didRepository.retrieveMnemonic()
            .flatMap { ethereumRepository.getEthCredentials(it) }
            .flatMap { ethereumRepository.getGasEstimations(it) }
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

    override fun startCombinedXorErcTransfer(partialAmount: BigDecimal, amount: BigDecimal, ethAddress: String, transactionFee: String): Completable {
        return didRepository.retrieveMnemonic()
            .flatMap { ethereumRepository.getEthCredentials(it) }
            .flatMap { ethCredentials -> ethereumRepository.getEthWalletAddress(ethCredentials).map { Pair(it, ethCredentials) } }
            .flatMapCompletable {
                didRepository.getAccountId()
                    .flatMapCompletable { accountId ->
                        didRepository.retrieveKeypair()
                            .flatMapCompletable { keypair ->
                                ethereumRepository.startCombinedXorErcTransfer(partialAmount, amount, accountId, it.first, ethAddress, transactionFee, it.second, keypair)
                            }
                    }
            }
    }

    override fun startCombinedXorTransfer(partialAmount: BigDecimal, amount: BigDecimal, peerId: String, peerName: String, transactionFee: BigDecimal, description: String): Completable {
        return didRepository.retrieveKeypair()
            .flatMapCompletable { keyPair ->
                didRepository.retrieveMnemonic()
                    .flatMapCompletable {
                        ethereumRepository.getEthCredentials(it)
                            .flatMap { ethCreds -> ethereumRepository.getXorTokenAddress(ethCreds).map { Pair(ethCreds, it) } }
                            .flatMapCompletable { ethereumRepository.startCombinedXorTransfer(partialAmount, amount, peerId, peerName, transactionFee, description, it.first, keyPair, it.second) }
                    }
            }
    }
}