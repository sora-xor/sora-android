/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.DepositTransactionLocal
import jp.co.soramitsu.core_db.model.TransferTransactionLocal
import jp.co.soramitsu.core_db.model.WithdrawTransactionLocal
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumDatasource
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import jp.co.soramitsu.feature_ethereum_api.domain.model.GasEstimation
import jp.co.soramitsu.feature_ethereum_impl.BuildConfig
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthRegisterStateMapper
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthereumCredentialsMapper
import jp.co.soramitsu.feature_ethereum_impl.data.network.EthereumNetworkApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.SoranetApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.TransactionFactory
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.EthPublicKeyWithProof
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.KeccakProof
import jp.co.soramitsu.feature_ethereum_impl.data.repository.converter.EtherWeiConverter
import jp.co.soramitsu.feature_ethereum_impl.util.ContractsApiProvider
import jp.co.soramitsu.feature_ethereum_impl.util.Web3jBip32Crypto
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetBalance
import org.bouncycastle.util.encoders.Hex
import org.web3j.crypto.Sign
import org.web3j.protocol.Web3j
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
    private val api: EthereumNetworkApi,
    private val soranetApi: SoranetApi,
    private val web3j: Web3j,
    private val web3jBip32Crypto: Web3jBip32Crypto,
    private val serializer: Serializer,
    private val contractApiProvider: ContractsApiProvider,
    private val transactionFactory: TransactionFactory,
    private val etherWeiConverter: EtherWeiConverter,
    private val db: AppDatabase,
    private val appLinksProvider: AppLinksProvider,
    private val ethRegisterStateMapper: EthRegisterStateMapper
) : EthereumRepository {

    override fun getXorTokenAddress(ethCredentials: EthereumCredentials): Single<String> {
        return Single.fromCallable {
            val address = ethDataSource.retrieveXORAddress()

            if (address.isEmpty()) {
                val xorAddress = contractApiProvider.getSmartContractApi(ethCredentials).xorTokenInstance().send()
                    ?: ""
                ethDataSource.saveXORAddress(xorAddress)
                return@fromCallable xorAddress
            }

            address
        }
    }

    override fun transferXorErc20(to: String, amount: BigDecimal, ethCredentials: EthereumCredentials, xorTokenAddress: String): Completable {
        return Single.fromCallable { etherWeiConverter.fromEtherToWei(amount) }
            .map { contractApiProvider.getErc20ContractApi(ethCredentials, xorTokenAddress).transferXor(to, it).send() }
            .onErrorResumeNext { Single.error(SoraException.unexpectedError(it)) }
            .map { transactionReceipt ->
                val gasPrice = contractApiProvider.getGasPrice()
                Pair(transactionReceipt, gasPrice)
            }
            .doOnSuccess {
                val gasPrice = it.second
                val fee = etherWeiConverter.fromWeiToEther(it.first.gasUsed * gasPrice)
                val transaction = TransferTransactionLocal(it.first.transactionHash, TransferTransactionLocal.Status.PENDING, AssetHolder.SORA_XOR_ERC_20.id, "",
                    ethereumCredentialsMapper.getAddress(ethCredentials.privateKey), to, amount, Date().time / 1000, to, "", TransferTransactionLocal.Type.OUTGOING, fee)

                db.transactionDao().insert(transaction)
            }
            .ignoreElement()
    }

    override fun updateXorErc20AndEthBalance(ethCredentials: EthereumCredentials, ethWalletAddress: String, xorTokenAddress: String): Completable {
        return getXorErc20BalanceRemote(ethCredentials, ethWalletAddress, xorTokenAddress)
            .zipWith(
                getEthereumBalanceRemote(ethCredentials),
                BiFunction<AssetBalance, AssetBalance, List<AssetBalance>> { xorErc20Asset, ethereumAsset ->
                    mutableListOf(xorErc20Asset, ethereumAsset)
                })
            .doOnSuccess {
                db.runInTransaction {
                    it.forEach { db.assetDao().updateBalance(it.assetId, it.balance) }
                }
            }
            .ignoreElement()
    }

    private fun getXorErc20BalanceRemote(ethCredentials: EthereumCredentials, ethWalletAddress: String, xorTokenAddress: String): Single<AssetBalance> {
        return Single.fromCallable { contractApiProvider.getErc20ContractApi(ethCredentials, xorTokenAddress).xorBalance(ethWalletAddress).send() }
            .map { etherWeiConverter.fromWeiToEther(it) }
            .map { AssetBalance(AssetHolder.SORA_XOR_ERC_20.id, it) }
    }

    private fun getEthereumBalanceRemote(ethCredentials: EthereumCredentials): Single<AssetBalance> {
        return Single.fromCallable { ethereumCredentialsMapper.getAddress(ethCredentials.privateKey) }
            .map { web3j.ethGetBalance(it, DefaultBlockParameterName.LATEST).send().balance }
            .map { etherWeiConverter.fromWeiToEther(it) }
            .map { AssetBalance(AssetHolder.ETHER_ETH.id, it) }
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

    override fun calculateXorErc20TransferFee(): Single<BigDecimal> {
        return calculateXorErc20Fee(ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger())
    }

    override fun calculateXorErc20WithdrawFee(): Single<BigDecimal> {
        return calculateXorErc20Fee(ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())
    }

    private fun calculateXorErc20Fee(gasLimit: BigInteger): Single<BigDecimal> {
        return Single.fromCallable { contractApiProvider.setGasLimit(gasLimit) }
            .map { contractApiProvider.fetchGasPrice() }
            .map { it * gasLimit }
            .map { etherWeiConverter.fromWeiToEther(it) }
    }

    override fun registerEthAccount(accountId: String, serializedValue: String, keyPair: KeyPair): Completable {
        return transactionFactory.buildRegisterEthRequest(accountId, serializedValue, keyPair)
            .flatMap { pair ->
                api.ethRegister(pair.first)
                    .map { pair.second }
            }
            .ignoreElement()
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
                    signature.v.toString(16),
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
            }

            ethDataSource.retrieveEthereumCredentials()
        }
    }

    private fun generateEthCredentials(mnemonic: String): EthereumCredentials {
        val seed = web3jBip32Crypto.generateSeedFromMnemonic(mnemonic)
        val masterKeyPair = web3jBip32Crypto.generateECMasterKeyPair(seed)
        val bip44Keypair = web3jBip32Crypto.deriveECKeyPairFromMaster(masterKeyPair)
        val credentials = ethereumCredentialsMapper.getCredentialsFromECKeyPair(bip44Keypair)
        return EthereumCredentials(credentials.ecKeyPair.privateKey)
    }

    override fun startWithdraw(amount: BigDecimal, srcAccountId: String, ethAddress: String, transactionFee: String, keyPair: KeyPair): Completable {
        return transactionFactory.buildWithdrawTransaction(amount, srcAccountId, ethAddress, transactionFee, keyPair)
            .flatMap { result ->
                val secondsNow = Date().time / 1000
                val tx = WithdrawTransactionLocal(result.second, "", "", WithdrawTransactionLocal.Status.INTENT_STARTED, "", ethAddress, amount,
                    BigDecimal.ZERO, secondsNow, ethAddress, "", "", BigDecimal(transactionFee), BigDecimal.ZERO, contractApiProvider.getGasLimit(), contractApiProvider.getGasPrice())

                db.withdrawTransactionDao().insert(tx)

                api.withdraw(result.first)
                    .map { result.second }
            }
            .ignoreElement()
    }

    override fun startCombinedXorErcTransfer(
        partialAmount: BigDecimal,
        amount: BigDecimal,
        srcAccountId: String,
        withdrawEthAddress: String,
        transferEthAddress: String,
        transactionFee: String,
        ethCredentials: EthereumCredentials,
        keyPair: KeyPair
    ): Completable {
        return transactionFactory.buildWithdrawTransaction(partialAmount, srcAccountId, withdrawEthAddress, transactionFee, keyPair)
            .flatMap { txInfo ->
                getGasLimit()
                    .flatMap { gasLimit ->
                        getGasPrice()
                            .map { gasPrice ->
                                Triple(txInfo, gasLimit, gasPrice)
                            }
                    }
            }
            .map {
                val secondsNow = Date().time / 1000
                val minerFeeInEth = etherWeiConverter.fromWeiToEther(it.second * it.third)
                val gasPriceInGwei = etherWeiConverter.fromWeiToGwei(it.third)
                val tx = WithdrawTransactionLocal(it.first.second, "", "", WithdrawTransactionLocal.Status.INTENT_STARTED, "", transferEthAddress,
                    partialAmount, amount, secondsNow, withdrawEthAddress, transferEthAddress, "", BigDecimal(transactionFee), minerFeeInEth, it.second, gasPriceInGwei)

                db.withdrawTransactionDao().insert(tx)
                it.first
            }
            .flatMap { result ->
                api.withdraw(result.first)
                    .map {
                        result.second
                    }
            }
            .ignoreElement()
    }

    override fun startCombinedXorTransfer(partialAmount: BigDecimal, amount: BigDecimal, transferAccountId: String, transferName: String, transactionFee: BigDecimal, description: String, ethCredentials: EthereumCredentials, keyPair: KeyPair, xorTokenAddress: String): Completable {
        return Single.fromCallable { etherWeiConverter.fromEtherToWei(partialAmount) }
            .map { contractApiProvider.getErc20ContractApi(ethCredentials, xorTokenAddress).transferXor(BuildConfig.MASTER_CONTRACT_ADDRESS, it).send() }
            .flatMap { tx ->
                getGasLimit()
                    .flatMap { gasLimit ->
                        getGasPrice()
                            .map { gasPrice ->
                                Triple(tx, gasLimit, gasPrice)
                            }
                    }
            }
            .map {
                val secondsNow = Date().time / 1000
                val minerFeeInEth = etherWeiConverter.fromWeiToEther(it.second * it.third)
                val tx = DepositTransactionLocal(it.first.transactionHash, "", DepositTransactionLocal.Status.DEPOSIT_PENDING,
                    AssetHolder.SORA_XOR_ERC_20.id, description, "Deposit", partialAmount, amount, secondsNow, transferAccountId, "", minerFeeInEth, transactionFee)

                db.depositTransactionDao().insert(tx)
            }
            .ignoreElement()
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

    override fun confirmWithdraw(ethCredentials: EthereumCredentials, amount: BigDecimal, txHash: String, accountId: String, gasPrice: BigInteger, gasLimit: BigInteger, xorTokenAddress: String): Single<String> {
        return soranetApi.getWithdrawalProofs(accountId)
            .map { val isUsed = contractApiProvider.getSmartContractApi(ethCredentials).used(Hex.decode(txHash)).send(); Pair(it, isUsed > BigInteger.ZERO) }
            .flatMap {
                if (it.second) {
                    Single.just("")
                } else {
                    contractApiProvider.setGasLimit(gasLimit)
                    contractApiProvider.setGasPrice(gasPrice)

                    val proofs = it.first.proofs.filter { it.irohaTxHash.toLowerCase() == txHash.toLowerCase() }.map { it.proofs }.flatten()
                    val v = proofs.map { it.v }.map { Hex.decode(it) }.map { BigInteger(it) }
                    val r = proofs.map { it.r }.map { Hex.decode(it) }
                    val s = proofs.map { it.s }.map { Hex.decode(it) }

                    val ethAddress = it.first.proofs.firstOrNull { it.irohaTxHash.toLowerCase() == txHash.toLowerCase() }?.to
                        ?: ""
                    val result = mintTokensByPeers(ethCredentials, amount, txHash, ethAddress, v, r, s, ethAddress, xorTokenAddress)
                    result
                }
            }
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
        xorTokenAddress: String
    ): Single<String> {
        return Single.fromCallable {
            val txHashBytes = Hex.decode(txHash)
            val result = contractApiProvider.getSmartContractApi(ethCredentials).mintTokensByPeers(amount, beneficiary, txHashBytes, v, r, s, from, xorTokenAddress).send()
            result.transactionHash
        }
    }

    override fun observeEthRegisterState(): Observable<EthRegisterState.State> {
        return ethDataSource.observeEthRegisterState()
    }

    override fun getGasEstimations(ethCredentials: EthereumCredentials): Single<Gas> {
        return Single.zip(
            getGasLimit(),
            getGasPrice(),
            BiFunction<BigInteger, BigInteger, Gas> { gasLimit, gasPrice ->
                val estimations = listOf(
                    getSlowEstimations(gasLimit, gasPrice),
                    getRegularEstimations(gasLimit, gasPrice),
                    getFastEstimations(gasLimit, gasPrice)
                )

                val gasPriceInGwei = etherWeiConverter.fromWeiToGwei(gasPrice)
                Gas(gasPriceInGwei, gasLimit, estimations)
            }
        )
    }

    private fun getSlowEstimations(gasLimit: BigInteger, gasPrice: BigInteger): GasEstimation {
        val amount = gasLimit - BigInteger.TEN
        val amountWithPrice = amount * gasPrice
        val amountInEth = etherWeiConverter.fromWeiToEther(amountWithPrice)
        return GasEstimation(GasEstimation.Type.SLOW, amount, amountInEth, 600)
    }

    private fun getRegularEstimations(gasLimit: BigInteger, gasPrice: BigInteger): GasEstimation {
        val amountWithPrice = gasLimit * gasPrice
        val amountInEth = etherWeiConverter.fromWeiToEther(amountWithPrice)
        return GasEstimation(GasEstimation.Type.REGULAR, gasLimit, amountInEth, 90)
    }

    private fun getFastEstimations(gasLimit: BigInteger, gasPrice: BigInteger): GasEstimation {
        val amount = gasLimit + BigInteger.TEN
        val amountWithPrice = amount * gasPrice
        val amountInEth = etherWeiConverter.fromWeiToEther(amountWithPrice)
        return GasEstimation(GasEstimation.Type.FAST, amount, amountInEth, 20)
    }

    override fun getBlockChainExplorerUrl(transactionHash: String): Single<String> {
        return Single.fromCallable {
            appLinksProvider.etherscanExplorerUrl + transactionHash
        }
    }

    override fun getActualEthRegisterState(): Single<EthRegisterState.State> {
        return getLocalEthRegisterState()
            .flatMap {
                if (EthRegisterState.State.NONE == it.state) {
                    api.getEthRegisterState()
                        .map { ethRegisterStateMapper.map(it) }
                        .onErrorResumeNext {
                            if (it is SoraException && ResponseCode.BOUND_ETH_ADDRESS_NOT_FOUND == it.errorResponseCode) {
                                val state = EthRegisterState(EthRegisterState.State.NONE, null)
                                Single.just(state)
                            } else {
                                Single.error(it)
                            }
                        }
                        .doOnSuccess { ethDataSource.saveEthRegisterState(it) }
                        .map { it.state }
                } else {
                    Single.just(it.state)
                }
            }
    }

    override fun updateTransactionStatuses(ethCredentials: EthereumCredentials, accountId: String, xorTokenAddress: String): Completable {
        return updateWithdrawTransactionStatuses(ethCredentials, accountId, xorTokenAddress)
            .andThen(updateTransferTransactionStatuses())
            .andThen(updateCombinedWithdrawTransactionStatuses(ethCredentials))
    }

    override fun processLastCombinedDepositTransaction(first: EthereumCredentials): Completable {
        return db.depositTransactionDao().getTransactionWaitingToFinish()
            .doOnSuccess {
                db.runInTransaction {
                    val status = getDepositStatus(it.depositTxHash)
                    db.depositTransactionDao().updateStatus(it.depositTxHash, status)
                }
            }
            .ignoreElement()
    }

    override fun processLastCombinedWithdrawTransaction(ethCredentials: EthereumCredentials, xorTokenAddress: String): Completable {
        return db.withdrawTransactionDao().getLastTransactionWaitingToFinish()
            .flatMap { tx ->
                setGasLimitRemote(tx.gasLimit)
                    .andThen(setGasPriceRemote(tx.gasPrice))
                    .andThen(finishCombinedWithdrawTransaction(tx.transferPeerId!!, tx.transferAmount, ethCredentials, xorTokenAddress))
                    .map { Pair(it, tx.intentTxHash) }
                    .toMaybe()
            }
            .doOnSuccess {
                db.runInTransaction {
                    db.withdrawTransactionDao().updateStatus(it.second, WithdrawTransactionLocal.Status.TRANSFER_PENDING)
                    db.withdrawTransactionDao().updateTransferHash(it.second, it.first)
                }
            }
            .ignoreElement()
    }

    private fun finishCombinedWithdrawTransaction(ethAddress: String, amount: BigDecimal, ethCredentials: EthereumCredentials, xorTokenAddress: String): Single<String> {
        return Single.fromCallable { etherWeiConverter.fromEtherToWei(amount) }
            .map { contractApiProvider.getErc20ContractApi(ethCredentials, xorTokenAddress).transferXor(ethAddress, it).send() }
            .onErrorResumeNext {
                Single.error(SoraException.unexpectedError(it))
            }
            .map { it.transactionHash }
    }

    private fun updateCombinedWithdrawTransactionStatuses(ethCredentials: EthereumCredentials): Completable {
        return db.withdrawTransactionDao().getTransactionsByStatus(WithdrawTransactionLocal.Status.TRANSFER_PENDING)
            .doOnSuccess {
                db.runInTransaction {
                    it.forEach {
                        val status = getCombinedWithdrawTransactionStatus(it.transferTxHash, it.intentTxHash, ethCredentials)
                        db.withdrawTransactionDao().updateStatus(it.intentTxHash, status)

                        if (status == WithdrawTransactionLocal.Status.TRANSFER_COMPLETED) {
                            db.transactionDao().updateStatus(it.intentTxHash, TransferTransactionLocal.Status.COMMITTED)
                        }
                    }
                }
            }
            .ignoreElement()
    }

    private fun updateWithdrawTransactionStatuses(ethCredentials: EthereumCredentials, accountId: String, xorTokenAddress: String): Completable {
        return Single.zip(
            db.withdrawTransactionDao().getTransactionsByStatus(WithdrawTransactionLocal.Status.CONFIRM_PENDING),
            db.withdrawTransactionDao().getTransactionsByStatus(WithdrawTransactionLocal.Status.INTENT_COMPLETED),
            BiFunction<List<WithdrawTransactionLocal>, List<WithdrawTransactionLocal>, List<WithdrawTransactionLocal>>
            { list1, list2 -> list1 + list2 })
            .doOnSuccess {
                db.runInTransaction {
                    it.forEach {
                        var status = getWithdrawTransactionStatus(it.confirmTxHash, it.intentTxHash, ethCredentials)

                        if (it.status == WithdrawTransactionLocal.Status.INTENT_COMPLETED && status == WithdrawTransactionLocal.Status.CONFIRM_PENDING && it.confirmTxHash.isEmpty() && it.gasLimit > BigInteger.ZERO && it.gasPrice > BigInteger.ZERO) {
                            confirmWithdraw(ethCredentials, it.withdrawAmount, it.intentTxHash, accountId, it.gasPrice, it.gasLimit, xorTokenAddress)
                                .runCatching {
                                    val hash = blockingGet()
                                    db.withdrawTransactionDao().updateConfirmTxHash(it.intentTxHash, hash)
                                }
                                .onFailure {
                                    status = WithdrawTransactionLocal.Status.CONFIRM_FAILED
                                }
                        }

                        db.withdrawTransactionDao().updateStatus(it.intentTxHash, status)

                        if (it.transferAmount == BigDecimal.ZERO) {
                            val transferStatus = when (status) {
                                WithdrawTransactionLocal.Status.INTENT_FAILED, WithdrawTransactionLocal.Status.CONFIRM_FAILED, WithdrawTransactionLocal.Status.TRANSFER_FAILED -> TransferTransactionLocal.Status.REJECTED
                                WithdrawTransactionLocal.Status.INTENT_PENDING, WithdrawTransactionLocal.Status.CONFIRM_PENDING, WithdrawTransactionLocal.Status.TRANSFER_PENDING, WithdrawTransactionLocal.Status.INTENT_COMPLETED, WithdrawTransactionLocal.Status.INTENT_STARTED -> TransferTransactionLocal.Status.PENDING
                                WithdrawTransactionLocal.Status.CONFIRM_COMPLETED, WithdrawTransactionLocal.Status.TRANSFER_COMPLETED -> TransferTransactionLocal.Status.COMMITTED
                            }

                            db.transactionDao().updateStatus(it.intentTxHash, transferStatus)
                        }
                    }
                }
            }
            .ignoreElement()
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

    private fun getDepositStatus(txHash: String): DepositTransactionLocal.Status {
        val receipt = web3j.ethGetTransactionReceipt(txHash).send()
        val blockNumberRaw = receipt.transactionReceipt.get().blockNumberRaw

        return if (blockNumberRaw == null) {
            DepositTransactionLocal.Status.DEPOSIT_PENDING
        } else {
            if (receipt.hasError()) {
                DepositTransactionLocal.Status.DEPOSIT_FAILED
            } else {
                DepositTransactionLocal.Status.DEPOSIT_COMPLETED
            }
        }
    }

    private fun getTransactionStatus(txHash: String): TransferTransactionLocal.Status {
        val receipt = web3j.ethGetTransactionReceipt(txHash).send()
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

    private fun getWithdrawTransactionStatus(confirmTxHash: String, intentTxHash: String, ethCredentials: EthereumCredentials): WithdrawTransactionLocal.Status {
        return if (confirmTxHash.isEmpty()) {
            val isUsed = contractApiProvider.getSmartContractApi(ethCredentials).used(Hex.decode(intentTxHash)).send()
            if (isUsed > BigInteger.ZERO) {
                WithdrawTransactionLocal.Status.CONFIRM_COMPLETED
            } else {
                WithdrawTransactionLocal.Status.CONFIRM_PENDING
            }
        } else {
            val receipt = web3j.ethGetTransactionReceipt(confirmTxHash).send()

            if (receipt.transactionReceipt.get().blockNumberRaw == null) {
                WithdrawTransactionLocal.Status.CONFIRM_PENDING
            } else {
                if (receipt.hasError()) {
                    WithdrawTransactionLocal.Status.CONFIRM_FAILED
                } else {
                    WithdrawTransactionLocal.Status.CONFIRM_COMPLETED
                }
            }
        }
    }

    private fun getCombinedWithdrawTransactionStatus(transferTxHash: String, intentTxHash: String, ethCredentials: EthereumCredentials): WithdrawTransactionLocal.Status {
        return if (transferTxHash.isEmpty()) {
            val isUsed = contractApiProvider.getSmartContractApi(ethCredentials).used(Hex.decode(intentTxHash)).send()
            if (isUsed > BigInteger.ZERO) {
                WithdrawTransactionLocal.Status.TRANSFER_COMPLETED
            } else {
                WithdrawTransactionLocal.Status.TRANSFER_PENDING
            }
        } else {
            val receipt = web3j.ethGetTransactionReceipt(transferTxHash).send()

            if (receipt.result == null) {
                WithdrawTransactionLocal.Status.TRANSFER_PENDING
            } else {
                if (receipt.transactionReceipt.get().blockNumberRaw == null) {
                    WithdrawTransactionLocal.Status.TRANSFER_PENDING
                } else {
                    if (receipt.hasError()) {
                        WithdrawTransactionLocal.Status.TRANSFER_FAILED
                    } else {
                        WithdrawTransactionLocal.Status.TRANSFER_COMPLETED
                    }
                }
            }
        }
    }

    private fun getLocalEthRegisterState(): Single<EthRegisterState> {
        return Single.fromCallable {
            ethDataSource.getEthRegisterState()
        }
    }
}