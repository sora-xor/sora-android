/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.TransferTransactionLocal
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumDatasource
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import jp.co.soramitsu.feature_ethereum_api.domain.model.GasEstimation
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthRegisterStateMapper
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthereumCredentialsMapper
import jp.co.soramitsu.feature_ethereum_impl.data.network.TransactionFactory
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.EthPublicKeyWithProof
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.KeccakProof
import jp.co.soramitsu.feature_ethereum_impl.data.repository.converter.EtherWeiConverter
import jp.co.soramitsu.feature_ethereum_impl.util.ContractsApiProvider
import jp.co.soramitsu.feature_ethereum_impl.util.Web3jBip32Crypto
import jp.co.soramitsu.feature_ethereum_impl.util.Web3jProvider
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetBalance
import org.bouncycastle.util.encoders.Hex
import org.web3j.crypto.Sign
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.security.KeyPair
import java.util.Date
import javax.inject.Inject

class EthereumRepositoryImpl @Inject constructor(
    private val ethDataSource: EthereumDatasource,
    private val ethereumCredentialsMapper: EthereumCredentialsMapper,
    private val web3jProvider: Web3jProvider,
    private val web3jBip32Crypto: Web3jBip32Crypto,
    private val serializer: Serializer,
    private val contractApiProvider: ContractsApiProvider,
    private val transactionFactory: TransactionFactory,
    private val etherWeiConverter: EtherWeiConverter,
    private val db: AppDatabase,
    private val ethRegisterStateMapper: EthRegisterStateMapper,
) : EthereumRepository {

    companion object {
        private val EMPTY_PROOF_HASH = ByteArray(32)
    }

    override fun getValTokenAddress(ethCredentials: EthereumCredentials): Single<String> {
        return Single.fromCallable {
            contractApiProvider.getSmartContractApi(ethCredentials).valTokenInstance().send()
        }
    }

    override fun transferValErc20(
        to: String,
        amount: BigDecimal,
        ethCredentials: EthereumCredentials,
        tokenAddress: String
    ): Completable {
        return Single.fromCallable { etherWeiConverter.fromEtherToWei(amount) }
            .map {
                contractApiProvider.getErc20ContractApi(ethCredentials, tokenAddress)
                    .transferVal(to, it).send()
            }
            .onErrorResumeNext { Single.error(SoraException.unexpectedError(it)) }
            .doOnSuccess {
                val gasPrice = contractApiProvider.getGasPrice()
                val gasLimit = contractApiProvider.getGasLimit()

                val fee = etherWeiConverter.fromWeiToEther(gasLimit * gasPrice)
                val transaction = TransferTransactionLocal(
                    it.transactionHash,
                    TransferTransactionLocal.Status.PENDING,
                    "AssetHolder.SORA_VAL_ERC_20.id",
                    "",
                    amount,
                    Date().time / 1000,
                    to,
                    TransferTransactionLocal.Type.OUTGOING,
                    fee,
                    null,
                    null
                )

                db.transactionDao().insert(transaction)
            }
            .ignoreElement()
    }

    override fun updateValErc20AndEthBalance(
        ethCredentials: EthereumCredentials,
        ethWalletAddress: String,
        tokenAddress: String
    ): Completable {
        return getValErc20BalanceRemote(ethCredentials, ethWalletAddress, tokenAddress)
            .zipWith(
                getEthereumBalanceRemote(ethCredentials),
                BiFunction<AssetBalance, AssetBalance, List<AssetBalance>> { valErc20Asset, ethereumAsset ->
                    mutableListOf(valErc20Asset, ethereumAsset)
                }
            )
            .doOnSuccess {
                db.runInTransaction {
                    it.forEach { db.assetDao().updateBalance(it.assetId, it.balance) }
                }
            }
            .ignoreElement()
    }

    private fun getValErc20BalanceRemote(
        ethCredentials: EthereumCredentials,
        ethWalletAddress: String,
        tokenAddress: String
    ): Single<AssetBalance> {
        return Single.fromCallable {
            contractApiProvider.getErc20ContractApi(
                ethCredentials,
                tokenAddress
            ).valBalance(ethWalletAddress).send()
        }
            .map { etherWeiConverter.fromWeiToEther(it) }
            .map { AssetBalance("AssetHolder.SORA_VAL_ERC_20.id", it) }
    }

    private fun getEthereumBalanceRemote(ethCredentials: EthereumCredentials): Single<AssetBalance> {
        return Single.fromCallable { ethereumCredentialsMapper.getAddress(ethCredentials.privateKey) }
            .map {
                web3jProvider.web3j.ethGetBalance(it, DefaultBlockParameterName.LATEST)
                    .send().balance
            }
            .map { etherWeiConverter.fromWeiToEther(it) }
            .map { AssetBalance("AssetHolder.ETHER_ETH.id", it) }
    }

    override fun setGasPrice(gasPriceInGwei: BigInteger): Single<BigDecimal> {
        return setGasPriceRemote(gasPriceInGwei)
            .andThen(getGasLimit())
            .map { etherWeiConverter.fromGweiToWei(gasPriceInGwei) * it }
            .map { etherWeiConverter.fromWeiToEther(it) }
    }

    private fun setGasPriceRemote(gasPriceInGwei: BigInteger): Completable {
        return Completable.fromAction {
            val gasPrice = etherWeiConverter.fromGweiToWei(gasPriceInGwei)
            contractApiProvider.setGasPrice(gasPrice)
        }
    }

    override fun setGasLimit(gasLimit: BigInteger): Single<BigDecimal> {
        return setGasLimitRemote(gasLimit)
            .andThen(getGasPrice())
            .map { gasLimit * it }
            .map { etherWeiConverter.fromWeiToEther(it) }
    }

    private fun getGasPrice(): Single<BigInteger> {
        return Single.fromCallable {
            contractApiProvider.getGasPrice()
        }
    }

    private fun getGasLimit(): Single<BigInteger> {
        return Single.fromCallable {
            contractApiProvider.getGasLimit()
        }
    }

    private fun setGasLimitRemote(gasLimit: BigInteger): Completable {
        return Completable.fromAction {
            contractApiProvider.setGasLimit(gasLimit)
        }
    }

    override fun calculateValErc20TransferFee(): Single<BigDecimal> {
        return calculateValErc20Fee(ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger())
    }

    override fun calculateValErc20WithdrawFee(): Single<BigDecimal> {
        return calculateValErc20Fee(ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())
    }

    override fun calculateValErc20CombinedFee(): Single<BigDecimal> {
        return calculateValErc20Fee(ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger() + ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger())
    }

    private fun calculateValErc20Fee(gasLimit: BigInteger): Single<BigDecimal> {
        return Single.fromCallable { contractApiProvider.setGasLimit(gasLimit) }
            .map { contractApiProvider.fetchGasPrice() }
            .map { etherWeiConverter.fromWeiToGwei(it) * gasLimit }
            .map { etherWeiConverter.fromGweiToEther(it) }
    }

    override fun getEthWalletAddress(ethCredentials: EthereumCredentials): Single<String> {
        return Single.fromCallable {
            ethereumCredentialsMapper.getAddress(ethCredentials.privateKey)
        }
    }

    override fun getSerializedProof(ethCredentials: EthereumCredentials): Single<String> {
        return Single.fromCallable {
            val privateKey = ethCredentials.privateKey
            val publicKey = ethereumCredentialsMapper.getPublicKey(privateKey)
            val address = ethereumCredentialsMapper.getAddress(privateKey)
            val dataToSign = prepareDataToSign(address)
            val signature = sign(dataToSign, privateKey)

            val proof = EthPublicKeyWithProof(
                publicKey.toString(),
                KeccakProof(
                    signature.v.toString(charset("UTF-16")),
                    Hex.toHexString(signature.r),
                    Hex.toHexString(signature.s)
                )
            )

            serializer.serialize(proof)
        }
    }

    private fun sign(message: ByteArray, privateKey: BigInteger): Sign.SignatureData {
        val ecKeyPair = ethereumCredentialsMapper.getCredentials(privateKey).ecKeyPair
        return Sign.signMessage(message, ecKeyPair)
    }

    private fun prepareDataToSign(data: String): ByteArray {
        val dat = Numeric.hexStringToByteArray(data)
        return ("\u0019Ethereum Signed Message:\n" + (dat.size)).toByteArray() + dat
    }

    override fun getEthCredentials(mnemonic: String): Single<EthereumCredentials> {
        return Single.fromCallable {
            val credentials = ethDataSource.retrieveEthereumCredentials()
            if (credentials == null) {
                val ethCredentials = generateEthCredentials(mnemonic)
                ethDataSource.saveEthereumCredentials(ethCredentials)

                ethCredentials
            } else {
                credentials
            }
        }
    }

    private fun generateEthCredentials(mnemonic: String): EthereumCredentials {
        val seed = web3jBip32Crypto.generateSeedFromMnemonic(mnemonic)
        val masterKeyPair = web3jBip32Crypto.generateECMasterKeyPair(seed)
        val bip44Keypair = web3jBip32Crypto.deriveECKeyPairFromMaster(masterKeyPair)
        val credentials = ethereumCredentialsMapper.getCredentialsFromECKeyPair(bip44Keypair)
        return EthereumCredentials(credentials.ecKeyPair.privateKey)
    }

    override fun startWithdraw(
        amount: BigDecimal,
        srcAccountId: String,
        ethAddress: String,
        transactionFee: String,
        keyPair: KeyPair
    ): Completable {
        return Completable.complete()
    }

    override fun startCombinedValErcTransfer(
        partialAmount: BigDecimal,
        amount: BigDecimal,
        srcAccountId: String,
        withdrawEthAddress: String,
        transferEthAddress: String,
        transactionFee: String,
        ethCredentials: EthereumCredentials,
        keyPair: KeyPair
    ): Completable {
        return Completable.complete()
    }

    override fun startCombinedValTransfer(
        partialAmount: BigDecimal,
        amount: BigDecimal,
        transferAccountId: String,
        transferName: String,
        transactionFee: BigDecimal,
        description: String,
        ethCredentials: EthereumCredentials,
        keyPair: KeyPair,
        tokenAddress: String
    ): Completable {
        return Completable.complete()
    }

    override fun getEthRegistrationState(): EthRegisterState {
        return ethDataSource.getEthRegisterState()
    }

    override fun registrationStarted(operationId: String) {
        val ethRegisterState = EthRegisterState(EthRegisterState.State.IN_PROGRESS, operationId)
        ethDataSource.saveEthRegisterState(ethRegisterState)
    }

    override fun registrationCompleted(operationId: String) {
        val ethRegisterState = EthRegisterState(EthRegisterState.State.REGISTERED, operationId)
        ethDataSource.saveEthRegisterState(ethRegisterState)
    }

    override fun registrationFailed(operationId: String) {
        val ethRegisterState = EthRegisterState(EthRegisterState.State.FAILED, operationId)
        ethDataSource.saveEthRegisterState(ethRegisterState)
    }

    override fun confirmWithdraw(
        ethCredentials: EthereumCredentials,
        amount: BigDecimal,
        txHash: String,
        accountId: String,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        tokenAddress: String
    ): Single<String> {
        return Single.fromCallable { "" }
    }

    private fun mintTokensByPeers(
        ethCredentials: EthereumCredentials,
        amount: BigDecimal,
        txHash: String,
        beneficiary: String,
        v: List<BigInteger>,
        r: List<ByteArray>,
        s: List<ByteArray>,
        from: String,
        tokenAddress: String
    ): Single<String> {
        return Single.fromCallable {
            val txHashBytes = Hex.decode(txHash)
            val result = contractApiProvider.getSmartContractApi(ethCredentials)
                .mintTokensByPeers(amount, beneficiary, txHashBytes, v, r, s, from, tokenAddress)
                .send()
            result.transactionHash
        }
    }

    override fun observeEthRegisterState(): Observable<EthRegisterState.State> {
        return ethDataSource.observeEthRegisterState()
    }

    override fun getGasEstimations(
        gasLimit: BigInteger,
        ethCredentials: EthereumCredentials
    ): Single<Gas> {
        return getGasPrice()
            .map { gasPrice ->
                val estimations = listOf(
                    getSlowEstimations(gasLimit, gasPrice),
                    getRegularEstimations(gasLimit, gasPrice),
                    getFastEstimations(gasLimit, gasPrice)
                )

                var gasPriceInGwei = etherWeiConverter.fromWeiToGwei(gasPrice)

                if (gasPriceInGwei == BigInteger.ZERO) {
                    gasPriceInGwei = BigInteger.ONE
                }

                Gas(gasPriceInGwei, gasLimit, estimations)
            }
    }

    private fun getSlowEstimations(gasLimit: BigInteger, gasPrice: BigInteger): GasEstimation {
        val amount = gasLimit - BigInteger.TEN
        val gweiPrice = etherWeiConverter.fromWeiToGwei(gasPrice)
        val amountWithPrice = amount * gweiPrice
        val amountInEth = etherWeiConverter.fromGweiToEther(amountWithPrice)
        return GasEstimation(GasEstimation.Type.SLOW, amount, amountInEth, 600)
    }

    private fun getRegularEstimations(gasLimit: BigInteger, gasPrice: BigInteger): GasEstimation {
        val gweiPrice = etherWeiConverter.fromWeiToGwei(gasPrice)
        val amountWithPrice = gasLimit * gweiPrice
        val amountInEth = etherWeiConverter.fromGweiToEther(amountWithPrice)
        return GasEstimation(GasEstimation.Type.REGULAR, gasLimit, amountInEth, 90)
    }

    private fun getFastEstimations(gasLimit: BigInteger, gasPrice: BigInteger): GasEstimation {
        val amount = gasLimit + BigInteger.TEN
        val gweiPrice = etherWeiConverter.fromWeiToGwei(gasPrice)
        val amountWithPrice = amount * gweiPrice
        val amountInEth = etherWeiConverter.fromGweiToEther(amountWithPrice)
        return GasEstimation(GasEstimation.Type.FAST, amount, amountInEth, 20)
    }

    override fun getBlockChainExplorerUrl(transactionHash: String): Single<String> {
        return Single.fromCallable {
            ""
        }
    }

    override fun getActualEthRegisterState(): Single<EthRegisterState.State> {
        return Single.fromCallable { EthRegisterState.State.NONE }
    }

    override fun isBridgeEnabled(ethCredentials: EthereumCredentials): Single<Boolean> {
        return Single.fromCallable {
            val hash = contractApiProvider.getSmartContractApi(ethCredentials).proof().send()
            !EMPTY_PROOF_HASH.contentEquals(hash)
        }
    }

    private fun finishCombinedWithdrawTransaction(
        ethAddress: String,
        amount: BigDecimal,
        ethCredentials: EthereumCredentials,
        tokenAddress: String
    ): Single<String> {
        return Single.fromCallable { etherWeiConverter.fromEtherToWei(amount) }
            .map {
                contractApiProvider.getErc20ContractApi(ethCredentials, tokenAddress)
                    .transferVal(ethAddress, it).send()
            }
            .onErrorResumeNext {
                Single.error(SoraException.unexpectedError(it))
            }
            .map { it.transactionHash }
    }

    private fun updateTransferTransactionStatuses(): Completable {
        return db.transactionDao().getPendingEthereumTransactions()
            .doOnSuccess {
                db.runInTransaction {
                    it.forEach {
                        val status = getTransactionStatus(it.txHash)
                        db.transactionDao().updateStatus(it.txHash, status)
                    }
                }
            }
            .ignoreElement()
    }

    private fun getTransactionStatus(txHash: String): TransferTransactionLocal.Status {
        val receipt = web3jProvider.web3j.ethGetTransactionReceipt(txHash).send()
        val blockNumberRaw = receipt.transactionReceipt.get().blockNumberRaw

        return if (blockNumberRaw == null) {
            TransferTransactionLocal.Status.PENDING
        } else {
            if (receipt.hasError()) {
                TransferTransactionLocal.Status.REJECTED
            } else {
                TransferTransactionLocal.Status.COMMITTED
            }
        }
    }
}
