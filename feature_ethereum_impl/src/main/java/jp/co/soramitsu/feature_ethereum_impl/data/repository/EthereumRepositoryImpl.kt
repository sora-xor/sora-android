/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.repository

import java.math.BigDecimal
import java.math.BigInteger
import java.security.KeyPair
import javax.inject.Inject
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumDatasource
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import jp.co.soramitsu.feature_ethereum_api.domain.model.GasEstimation
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthereumCredentialsMapper
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.EthPublicKeyWithProof
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.KeccakProof
import jp.co.soramitsu.feature_ethereum_impl.data.repository.converter.EtherWeiConverter
import jp.co.soramitsu.feature_ethereum_impl.util.ContractsApiProvider
import jp.co.soramitsu.feature_ethereum_impl.util.Web3jBip32Crypto
import jp.co.soramitsu.feature_ethereum_impl.util.Web3jProvider
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
import org.web3j.crypto.Sign
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.utils.Numeric

class EthereumRepositoryImpl @Inject constructor(
    private val ethDataSource: EthereumDatasource,
    private val ethereumCredentialsMapper: EthereumCredentialsMapper,
    private val web3jProvider: Web3jProvider,
    private val web3jBip32Crypto: Web3jBip32Crypto,
    private val contractApiProvider: ContractsApiProvider,
    private val etherWeiConverter: EtherWeiConverter,
) : EthereumRepository {

    companion object {
        private val EMPTY_PROOF_HASH = ByteArray(32)
    }

    override fun getValTokenAddress(ethCredentials: EthereumCredentials): String {
        return contractApiProvider.getSmartContractApi(ethCredentials).valTokenInstance().send()
    }

    override fun transferValErc20(
        to: String,
        amount: BigDecimal,
        ethCredentials: EthereumCredentials,
        tokenAddress: String
    ) {
        try {
            val receipt = contractApiProvider.getErc20ContractApi(ethCredentials, tokenAddress)
                .transferVal(to, etherWeiConverter.fromEtherToWei(amount)).send()
            val gasPrice = contractApiProvider.getGasPrice()
            val gasLimit = contractApiProvider.getGasLimit()

            val fee = etherWeiConverter.fromWeiToEther(gasLimit * gasPrice)

            // db.transactionDao().insert(transaction)
        } catch (t: Throwable) {
            throw SoraException.unexpectedError(t)
        }
    }

    override fun updateValErc20AndEthBalance(
        ethCredentials: EthereumCredentials,
        ethWalletAddress: String,
        tokenAddress: String
    ) {
        val a = getValErc20BalanceRemote(ethCredentials, ethWalletAddress, tokenAddress)
        val b = getEthereumBalanceRemote(ethCredentials)
        mutableListOf(a, b)
//                db.runInTransaction {
//                    it.forEach { db.assetDao().updateBalance(it.assetId, it.balance) }
//                }
    }

    private fun getValErc20BalanceRemote(
        ethCredentials: EthereumCredentials,
        ethWalletAddress: String,
        tokenAddress: String
    ): AssetBalance {
        val c = contractApiProvider.getErc20ContractApi(
            ethCredentials,
            tokenAddress
        ).valBalance(ethWalletAddress).send()
            .let { etherWeiConverter.fromWeiToEther(it) }
        return AssetBalance(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
        )
    }

    private fun getEthereumBalanceRemote(ethCredentials: EthereumCredentials): AssetBalance {
        ethereumCredentialsMapper.getAddress(ethCredentials.privateKey)
            .let {
                web3jProvider.web3j.ethGetBalance(it, DefaultBlockParameterName.LATEST)
                    .send().balance
            }
            .let { etherWeiConverter.fromWeiToEther(it) }
        return AssetBalance(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
        )
    }

    override fun setGasPrice(gasPriceInGwei: BigInteger): BigDecimal {
        setGasPriceRemote(gasPriceInGwei)
        return getGasLimit()
            .let { etherWeiConverter.fromGweiToWei(gasPriceInGwei) * it }
            .let { etherWeiConverter.fromWeiToEther(it) }
    }

    private fun setGasPriceRemote(gasPriceInGwei: BigInteger) {
        val gasPrice = etherWeiConverter.fromGweiToWei(gasPriceInGwei)
        contractApiProvider.setGasPrice(gasPrice)
    }

    override fun setGasLimit(gasLimit: BigInteger): BigDecimal {
        setGasLimitRemote(gasLimit)
        return getGasPrice()
            .let { gasLimit * it }
            .let { etherWeiConverter.fromWeiToEther(it) }
    }

    private fun getGasPrice(): BigInteger {
        return contractApiProvider.getGasPrice()
    }

    private fun getGasLimit(): BigInteger {
        return contractApiProvider.getGasLimit()
    }

    private fun setGasLimitRemote(gasLimit: BigInteger) {
        contractApiProvider.setGasLimit(gasLimit)
    }

    override fun calculateValErc20TransferFee(): BigDecimal {
        return calculateValErc20Fee(ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger())
    }

    override fun calculateValErc20WithdrawFee(): BigDecimal {
        return calculateValErc20Fee(ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())
    }

    override fun calculateValErc20CombinedFee(): BigDecimal {
        return calculateValErc20Fee(ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger() + ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger())
    }

    private fun calculateValErc20Fee(gasLimit: BigInteger): BigDecimal {
        return contractApiProvider.setGasLimit(gasLimit)
            .let { contractApiProvider.fetchGasPrice() }
            .let { etherWeiConverter.fromWeiToGwei(it) * gasLimit }
            .let { etherWeiConverter.fromGweiToEther(it) }
    }

    override fun getEthWalletAddress(ethCredentials: EthereumCredentials): String {
        return ethereumCredentialsMapper.getAddress(ethCredentials.privateKey)
    }

    override fun getSerializedProof(ethCredentials: EthereumCredentials): String {
        val privateKey = ethCredentials.privateKey
        val publicKey = ethereumCredentialsMapper.getPublicKey(privateKey)
        val address = ethereumCredentialsMapper.getAddress(privateKey)
        val dataToSign = prepareDataToSign(address)
        val signature = sign(dataToSign, privateKey)

        val proof = EthPublicKeyWithProof(
            publicKey.toString(),
            KeccakProof(
                signature.v.toString(charset("UTF-16")),
                signature.r.toHexString(),
                signature.s.toHexString()
            )
        )

        return proof.toString()
    }

    private fun sign(message: ByteArray, privateKey: BigInteger): Sign.SignatureData {
        val ecKeyPair = ethereumCredentialsMapper.getCredentials(privateKey).ecKeyPair
        return Sign.signMessage(message, ecKeyPair)
    }

    private fun prepareDataToSign(data: String): ByteArray {
        val dat = Numeric.hexStringToByteArray(data)
        return ("\u0019Ethereum Signed Message:\n" + (dat.size)).toByteArray() + dat
    }

    override suspend fun getEthCredentials(mnemonic: String): EthereumCredentials {
        val credentials = ethDataSource.retrieveEthereumCredentials()
        return if (credentials == null) {
            val ethCredentials = generateEthCredentials(mnemonic)
            ethDataSource.saveEthereumCredentials(ethCredentials)

            ethCredentials
        } else {
            credentials
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
    ) {
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
    ) {
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
    ) {
    }

    override suspend fun getEthRegistrationState(): EthRegisterState {
        return ethDataSource.getEthRegisterState()
    }

    override suspend fun registrationStarted(operationId: String) {
        val ethRegisterState = EthRegisterState(EthRegisterState.State.IN_PROGRESS, operationId)
        ethDataSource.saveEthRegisterState(ethRegisterState)
    }

    override suspend fun registrationCompleted(operationId: String) {
        val ethRegisterState = EthRegisterState(EthRegisterState.State.REGISTERED, operationId)
        ethDataSource.saveEthRegisterState(ethRegisterState)
    }

    override suspend fun registrationFailed(operationId: String) {
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
    ): String {
        return ""
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
    ): String {
        val txHashBytes = txHash.fromHex()
        val result = contractApiProvider.getSmartContractApi(ethCredentials)
            .mintTokensByPeers(amount, beneficiary, txHashBytes, v, r, s, from, tokenAddress)
            .send()
        return result.transactionHash
    }

    override fun getGasEstimations(
        gasLimit: BigInteger,
        ethCredentials: EthereumCredentials
    ): Gas {
        return getGasPrice()
            .let { gasPrice ->
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

    override fun getBlockChainExplorerUrl(transactionHash: String): String {
        return ""
    }

    override fun getActualEthRegisterState(): EthRegisterState.State {
        return EthRegisterState.State.NONE
    }

    override fun isBridgeEnabled(ethCredentials: EthereumCredentials): Boolean {
        val hash = contractApiProvider.getSmartContractApi(ethCredentials).proof().send()
        return !EMPTY_PROOF_HASH.contentEquals(hash)
    }
}
