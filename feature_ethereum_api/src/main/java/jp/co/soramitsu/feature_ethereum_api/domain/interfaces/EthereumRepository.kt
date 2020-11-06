package jp.co.soramitsu.feature_ethereum_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import java.math.BigDecimal
import java.math.BigInteger
import java.security.KeyPair

interface EthereumRepository {

    fun getValTokenAddress(ethCredentials: EthereumCredentials): Single<String>

    fun registerEthAccount(accountId: String, serializedValue: String, keyPair: KeyPair): Completable

    fun getSerializedProof(ethCredentials: EthereumCredentials): Single<String>

    fun getEthCredentials(mnemonic: String): Single<EthereumCredentials>

    fun startWithdraw(amount: BigDecimal, srcAccountId: String, ethAddress: String, transactionFee: String, keyPair: KeyPair): Completable

    fun startCombinedValErcTransfer(partialAmount: BigDecimal, amount: BigDecimal, srcAccountId: String, withdrawEthAddress: String, transferEthAddress: String, transactionFee: String, ethCredentials: EthereumCredentials, keyPair: KeyPair): Completable

    fun startCombinedValTransfer(partialAmount: BigDecimal, amount: BigDecimal, transferAccountId: String, transferName: String, transactionFee: BigDecimal, description: String, ethCredentials: EthereumCredentials, keyPair: KeyPair, tokenAddress: String): Completable

    fun getEthWalletAddress(ethCredentials: EthereumCredentials): Single<String>

    fun calculateValErc20TransferFee(): Single<BigDecimal>

    fun calculateValErc20WithdrawFee(): Single<BigDecimal>

    fun setGasPrice(gasPriceInGwei: BigInteger): Single<BigDecimal>

    fun setGasLimit(gasLimit: BigInteger): Single<BigDecimal>

    fun transferValErc20(to: String, amount: BigDecimal, ethCredentials: EthereumCredentials, tokenAddress: String): Completable

    fun getEthRegistrationState(): EthRegisterState

    fun registrationStarted(operationId: String)

    fun registrationCompleted(operationId: String)

    fun registrationFailed(operationId: String)

    fun observeEthRegisterState(): Observable<EthRegisterState.State>

    fun getGasEstimations(ethCredentials: EthereumCredentials): Single<Gas>

    fun getBlockChainExplorerUrl(transactionHash: String): Single<String>

    fun updateValErc20AndEthBalance(ethCredentials: EthereumCredentials, ethWalletAddress: String, tokenAddress: String): Completable

    fun confirmWithdraw(ethCredentials: EthereumCredentials, amount: BigDecimal, txHash: String, accountId: String, gasPrice: BigInteger, gasLimit: BigInteger, tokenAddress: String): Single<String>

    fun getActualEthRegisterState(): Single<EthRegisterState.State>

    fun updateTransactionStatuses(ethCredentials: EthereumCredentials, accountId: String, tokenAddress: String): Completable

    fun processLastCombinedWithdrawTransaction(ethCredentials: EthereumCredentials, tokenAddress: String): Completable

    fun processLastCombinedDepositTransaction(first: EthereumCredentials): Completable

    fun isBridgeEnabled(ethCredentials: EthereumCredentials): Single<Boolean>
}