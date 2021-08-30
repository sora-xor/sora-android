package jp.co.soramitsu.feature_ethereum_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.TransferTransactionDao
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumDatasource
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import jp.co.soramitsu.feature_ethereum_api.domain.model.GasEstimation
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthRegisterStateMapper
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthereumCredentialsMapper
import jp.co.soramitsu.feature_ethereum_impl.data.network.ERC20ContractApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.SmartContractApi
import jp.co.soramitsu.feature_ethereum_impl.data.repository.converter.EtherWeiConverter
import jp.co.soramitsu.feature_ethereum_impl.util.ContractsApiProvider
import jp.co.soramitsu.feature_ethereum_impl.util.Web3jBip32Crypto
import jp.co.soramitsu.feature_ethereum_impl.util.Web3jProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runBlockingTest
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.verify
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.protocol.core.RemoteCall
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.Callable

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class EthereumRepositoryTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var web3jProvider: Web3jProvider

    @Mock
    private lateinit var web3jBip32Crypto: Web3jBip32Crypto

    @Mock
    private lateinit var ethereumDatasource: EthereumDatasource

    @Mock
    private lateinit var ethereumCredentialsMapper: EthereumCredentialsMapper

    @Mock
    private lateinit var serializer: Serializer

    @Mock
    private lateinit var contractApiProvider: ContractsApiProvider

    @Mock
    private lateinit var smartContractApi: SmartContractApi

    @Mock
    private lateinit var erc20ContractApi: ERC20ContractApi

    @Mock
    private lateinit var etherWeiConverter: EtherWeiConverter

    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var ethRegisterStateMapper: EthRegisterStateMapper

    private lateinit var ethereumRepository: EthereumRepository

    private val valTokenAddress = "valTokenAddress"
    private val txHash = "txHash"
    private val srcAccountId = "srcaccountId"
    private val address = "address"
    private val mnemonic =
        "ecology power suggest mad rally exit leg guilt entry bid cook boil blame cry grunt"
    private val amount = BigDecimal.ONE
    private val minerFee = "11.0"
    private val seed = mnemonic.toByteArray()
    private val ethereumCredentials =
        EthereumCredentials(BigInteger("4309705105768215758615629237602468660061307779130899782366233796951641406004"))
    private val ecKeyPair = Bip32ECKeyPair.generateKeyPair(seed)
    private val gasPrice = BigInteger.ONE
    private val gasLimit = BigInteger.ONE

    @Before
    fun setUp() {
        given(contractApiProvider.getGasPrice()).willReturn(gasPrice)
        given(contractApiProvider.getGasLimit()).willReturn(gasLimit)
        given(etherWeiConverter.fromWeiToEther(gasLimit * gasPrice)).willReturn(BigDecimal(minerFee))
        given(contractApiProvider.getSmartContractApi(ethereumCredentials)).willReturn(
            smartContractApi
        )
        given(
            contractApiProvider.getErc20ContractApi(
                ethereumCredentials,
                valTokenAddress
            )
        ).willReturn(erc20ContractApi)
        ethereumRepository = EthereumRepositoryImpl(
            ethereumDatasource,
            ethereumCredentialsMapper,
            web3jProvider,
            web3jBip32Crypto,
            serializer,
            contractApiProvider,
            etherWeiConverter,
            db,
            ethRegisterStateMapper,
        )
    }

    @Test
    fun `transfer Xor ERC20 transfer called`() {
        val transactionDao = mock(TransferTransactionDao::class.java)
        val remoteCall = mock(RemoteCall::class.java)
        val transactionReceipt = mock(TransactionReceipt::class.java)

        given(etherWeiConverter.fromEtherToWei(amount)).willReturn(amount.toBigInteger())
        given(
            erc20ContractApi.transferVal(
                address,
                amount.toBigInteger()
            )
        ).willReturn(remoteCall as RemoteCall<TransactionReceipt>?)
        //given(ethereumCredentialsMapper.getAddress(anyNonNull())).willReturn(address)
        given(remoteCall!!.send()).willReturn(transactionReceipt)
        //given(transactionReceipt.transactionHash).willReturn(txHash)

        ethereumRepository.transferValErc20(address, amount, ethereumCredentials, valTokenAddress)

        verify(erc20ContractApi).transferVal(address, amount.toBigInteger())
        verify(remoteCall).send()
    }

    @Test
    fun `observer eth registration state`() = runBlockingTest {
        val state = EthRegisterState.State.NONE
        given(ethereumDatasource.observeEthRegisterState()).willReturn(flow { emit(state) })

        val value = ethereumRepository.observeEthRegisterState().toList()
        assertEquals(state, value[0])
    }

    @Test
    fun `get eth registration state`() {
        val state = EthRegisterState(EthRegisterState.State.NONE, "")
        given(ethereumDatasource.getEthRegisterState()).willReturn(state)

        val actual = ethereumRepository.getEthRegistrationState()
        assertEquals(state, actual)
    }

    @Test
    fun `registration state called`() {
        val operationId = "operationId"
        val ethRegisterState = EthRegisterState(EthRegisterState.State.IN_PROGRESS, operationId)

        ethereumRepository.registrationStarted(operationId)

        verify(ethereumDatasource).saveEthRegisterState(ethRegisterState)
    }

    @Test
    fun `registration completed called`() {
        val operationId = "operationId"
        val ethRegisterState = EthRegisterState(EthRegisterState.State.REGISTERED, operationId)

        ethereumRepository.registrationCompleted(operationId)

        verify(ethereumDatasource).saveEthRegisterState(ethRegisterState)
    }

    @Test
    fun `registration failed called`() {
        val operationId = "operationId"
        val ethRegisterState = EthRegisterState(EthRegisterState.State.FAILED, operationId)

        ethereumRepository.registrationFailed(operationId)

        verify(ethereumDatasource).saveEthRegisterState(ethRegisterState)
    }

    @Test
    fun `set gas limit called`() {
        val gasLimit = BigInteger.ONE
        val gasPrice = BigInteger.TEN
        val etherFee = BigDecimal.TEN

        given(etherWeiConverter.fromWeiToEther(BigInteger.TEN)).willReturn(etherFee)

        given(contractApiProvider.getGasPrice()).willReturn(gasPrice)

        assertEquals(etherFee, ethereumRepository.setGasLimit(gasLimit))

        verify(contractApiProvider).setGasLimit(gasLimit)
    }

    @Test
    fun `set gas price called`() {
        val gasLimit = BigInteger.ONE
        val gasPriceInWei = BigInteger.TEN
        val gasPriceInGwei = BigInteger("2")
        val etherFee = BigDecimal.TEN

        given(etherWeiConverter.fromWeiToEther(BigInteger.TEN)).willReturn(etherFee)
        given(etherWeiConverter.fromGweiToWei(gasPriceInGwei)).willReturn(gasPriceInWei)

        given(contractApiProvider.getGasLimit()).willReturn(gasLimit)

        assertEquals(etherFee, ethereumRepository.setGasPrice(gasPriceInGwei))

        verify(contractApiProvider).setGasPrice(gasPriceInWei)
    }

    @Test
    fun `get eth credentials from cache called`() {
        given(ethereumDatasource.retrieveEthereumCredentials()).willReturn(ethereumCredentials)

        assertEquals(ethereumCredentials, ethereumRepository.getEthCredentials(mnemonic))
    }

    @Test
    fun `get eth credentials called`() {
        val masterKeypair = Bip32ECKeyPair(BigInteger.ONE, BigInteger.ONE, 0, seed, null)
        val childKeypair =
            Bip32ECKeyPair(ethereumCredentials.privateKey, BigInteger.TEN, 1, seed, masterKeypair)
        val credentials = Credentials.create(childKeypair)

        given(web3jBip32Crypto.generateSeedFromMnemonic(mnemonic)).willReturn(seed)
        given(web3jBip32Crypto.generateECMasterKeyPair(seed)).willReturn(masterKeypair)
        given(web3jBip32Crypto.deriveECKeyPairFromMaster(masterKeypair)).willReturn(childKeypair)
        given(ethereumCredentialsMapper.getCredentialsFromECKeyPair(childKeypair)).willReturn(
            credentials
        )

        assertEquals(ethereumCredentials, ethereumRepository.getEthCredentials(mnemonic))

        verify(ethereumDatasource).saveEthereumCredentials(ethereumCredentials)
    }

    @Test
    fun `get gas estimations called`() {
        val slowGasLimit = gasLimit - BigInteger.TEN
        val slowGasAmount = slowGasLimit * gasPrice
        val slowGasAmountEth = slowGasAmount.toBigDecimal()
        given(etherWeiConverter.fromGweiToEther(slowGasAmount)).willReturn(slowGasAmountEth)
        given(contractApiProvider.getGasPrice()).willReturn(gasPrice)
        given(etherWeiConverter.fromWeiToGwei(gasPrice)).willReturn(gasPrice)

        val normalGasLimit = gasLimit
        val normalGasAmount = normalGasLimit * gasPrice
        val normalGasAmountEth = normalGasAmount.toBigDecimal()
        given(etherWeiConverter.fromGweiToEther(normalGasAmount)).willReturn(normalGasAmountEth)

        val fastGasLimit = gasLimit + BigInteger.TEN
        val fastGasAmount = fastGasLimit * gasPrice
        val fastGasAmountEth = fastGasAmount.toBigDecimal()
        given(etherWeiConverter.fromGweiToEther(fastGasAmount)).willReturn(fastGasAmountEth)

        val estimations = listOf(
            GasEstimation(GasEstimation.Type.SLOW, slowGasAmount, slowGasAmountEth, 600),
            GasEstimation(GasEstimation.Type.REGULAR, normalGasAmount, normalGasAmountEth, 90),
            GasEstimation(GasEstimation.Type.FAST, fastGasAmount, fastGasAmountEth, 20)
        )

        assertEquals(
            Gas(gasPrice, gasLimit, estimations),
            ethereumRepository.getGasEstimations(gasLimit, ethereumCredentials)
        )
    }

    @Test
    fun `is bridge enabled called`() {
        val txHash = Hex.toHexString("irohaTxHash1".toByteArray())
        val txHashBytes = Hex.decode(txHash)
        val userRemoteCall = RemoteCall<ByteArray>(Callable { txHashBytes })
        given(smartContractApi.proof()).willReturn(userRemoteCall)

        assertEquals(true, ethereumRepository.isBridgeEnabled(ethereumCredentials))
    }

    @Test
    fun `is bridge enabled called when not enabled`() {
        val userRemoteCall = RemoteCall<ByteArray>(Callable { ByteArray(32) })
        given(smartContractApi.proof()).willReturn(userRemoteCall)

        assertEquals(false, ethereumRepository.isBridgeEnabled(ethereumCredentials))
    }

    @Test
    fun `get val token address called`() {
        val userRemoteCall = RemoteCall<String>(Callable { valTokenAddress })
        given(smartContractApi.valTokenInstance()).willReturn(userRemoteCall)

        assertEquals(valTokenAddress, ethereumRepository.getValTokenAddress(ethereumCredentials))
    }

    @Test
    fun `get eth address called`() {
        given(ethereumCredentialsMapper.getAddress(ethereumCredentials.privateKey)).willReturn(
            address
        )

        assertEquals(address, ethereumRepository.getEthWalletAddress(ethereumCredentials))
    }

    @Test
    fun `calculate transfer fee called`() {
        val feeEther = BigDecimal.TEN

        given(contractApiProvider.fetchGasPrice()).willReturn(gasPrice)
        given(etherWeiConverter.fromWeiToGwei(gasPrice)).willReturn(gasPrice)
        given(etherWeiConverter.fromGweiToEther(gasPrice * ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger())).willReturn(
            feeEther
        )

        assertEquals(feeEther, ethereumRepository.calculateValErc20TransferFee())

        verify(contractApiProvider).setGasLimit(ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger())
    }

    @Test
    fun `calculate withdraw fee called`() {
        val feeEther = BigDecimal.TEN

        given(contractApiProvider.fetchGasPrice()).willReturn(gasPrice)
        given(etherWeiConverter.fromWeiToGwei(gasPrice)).willReturn(gasPrice)
        given(etherWeiConverter.fromGweiToEther(gasPrice * ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())).willReturn(
            feeEther
        )

        assertEquals(feeEther, ethereumRepository.calculateValErc20WithdrawFee())

        verify(contractApiProvider).setGasLimit(ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())
    }

    @Test
    fun `calculate combined fee called`() {
        val feeEther = BigDecimal.TEN
        val gasLimit =
            ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger() + ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger()

        given(contractApiProvider.fetchGasPrice()).willReturn(gasPrice)
        given(etherWeiConverter.fromWeiToGwei(gasPrice)).willReturn(gasPrice)
        given(etherWeiConverter.fromGweiToEther(gasPrice * gasLimit)).willReturn(feeEther)

        assertEquals(feeEther, ethereumRepository.calculateValErc20CombinedFee())

        verify(contractApiProvider).setGasLimit(gasLimit)
    }
}