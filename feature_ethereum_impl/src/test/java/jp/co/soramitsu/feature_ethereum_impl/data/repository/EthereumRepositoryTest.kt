/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import iroha.protocol.TransactionOuterClass
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.common.data.network.response.BaseResponse
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.TransferTransactionDao
import jp.co.soramitsu.core_db.dao.WithdrawTransactionDao
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumDatasource
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthRegisterStateMapper
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthereumCredentialsMapper
import jp.co.soramitsu.feature_ethereum_impl.data.network.ERC20ContractApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.EthereumNetworkApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.SmartContractApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.SoranetApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.TransactionFactory
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.KeccakProof
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.WithdrawalProof
import jp.co.soramitsu.feature_ethereum_impl.data.network.request.IrohaRequest
import jp.co.soramitsu.feature_ethereum_impl.data.network.response.WithdrawalProofsResponse
import jp.co.soramitsu.feature_ethereum_impl.data.repository.converter.EtherWeiConverter
import jp.co.soramitsu.feature_ethereum_impl.util.ContractsApiProvider
import jp.co.soramitsu.feature_ethereum_impl.util.EthereumConfigProvider
import jp.co.soramitsu.feature_ethereum_impl.util.Web3jBip32Crypto
import jp.co.soramitsu.feature_ethereum_impl.util.Web3jProvider
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import jp.co.soramitsu.test_shared.eqNonNull
import org.bouncycastle.util.encoders.Hex
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
import org.web3j.protocol.core.RemoteCall
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigDecimal
import java.math.BigInteger
import java.security.KeyPair
import java.util.Base64
import java.util.concurrent.Callable

@RunWith(MockitoJUnitRunner::class)
class EthereumRepositoryTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var web3jProvider: Web3jProvider
    @Mock private lateinit var ethereumConfigProvider: EthereumConfigProvider
    @Mock private lateinit var web3jBip32Crypto: Web3jBip32Crypto
    @Mock private lateinit var ethereumDatasource: EthereumDatasource
    @Mock private lateinit var ethereumCredentialsMapper: EthereumCredentialsMapper
    @Mock private lateinit var api: EthereumNetworkApi
    @Mock private lateinit var soranetApi: SoranetApi
    @Mock private lateinit var serializer: Serializer
    @Mock private lateinit var transactionFactory: TransactionFactory
    @Mock private lateinit var contractApiProvider: ContractsApiProvider
    @Mock private lateinit var smartContractApi: SmartContractApi
    @Mock private lateinit var erc20ContractApi: ERC20ContractApi
    @Mock private lateinit var etherWeiConverter: EtherWeiConverter
    @Mock private lateinit var db: AppDatabase
    @Mock private lateinit var ethRegisterStateMapper: EthRegisterStateMapper

    private lateinit var ethereumRepository: EthereumRepository

    private val xorTokenAddress = "xorTokenAddress"
    private val txHash = "txHash"
    private val srcAccountId = "srcaccountId"
    private val address = "address"
    private val mnemonic = "ecology power suggest mad rally exit leg guilt entry bid cook boil blame cry grunt"
    private val amount = BigDecimal.ONE
    private val minerFee = "11.0"
    private val seed = mnemonic.toByteArray()
    private val ethereumCredentials = EthereumCredentials(BigInteger("4309705105768215758615629237602468660061307779130899782366233796951641406004"))
    private val ecKeyPair = Bip32ECKeyPair.generateKeyPair(seed)
    private val gasPrice = BigInteger.ONE
    private val gasLimit = BigInteger.ONE

    @Before
    fun setUp() {
        given(contractApiProvider.getGasPrice()).willReturn(gasPrice)
        given(contractApiProvider.getGasLimit()).willReturn(gasLimit)
        given(etherWeiConverter.fromWeiToEther(gasLimit * gasPrice)).willReturn(BigDecimal(minerFee))
        given(contractApiProvider.getSmartContractApi(ethereumCredentials)).willReturn(smartContractApi)
        given(contractApiProvider.getErc20ContractApi(ethereumCredentials, xorTokenAddress)).willReturn(erc20ContractApi)
        ethereumRepository = EthereumRepositoryImpl(ethereumDatasource, ethereumCredentialsMapper, api, soranetApi, web3jProvider, web3jBip32Crypto,
            serializer, contractApiProvider, transactionFactory, etherWeiConverter, db, ethRegisterStateMapper, ethereumConfigProvider)
    }

    @Test
    fun `transfer Xor ERC20 transfer called`() {
        val transactionDao = mock(TransferTransactionDao::class.java)
        val remoteCall = mock(RemoteCall::class.java)
        val transactionReceipt = mock(TransactionReceipt::class.java)

        given(etherWeiConverter.fromEtherToWei(amount)).willReturn(amount.toBigInteger())
        given(erc20ContractApi.transferVal(address, amount.toBigInteger())).willReturn(remoteCall as RemoteCall<TransactionReceipt>?)
        given(ethereumCredentialsMapper.getAddress(anyNonNull())).willReturn(address)
        given(remoteCall!!.send()).willReturn(transactionReceipt)
        given(transactionReceipt.transactionHash).willReturn(txHash)
        given(db.transactionDao()).willReturn(transactionDao)

        ethereumRepository.transferValErc20(address, amount, ethereumCredentials, xorTokenAddress)
            .test()
            .assertComplete()

        verify(erc20ContractApi).transferVal(address, amount.toBigInteger())
        verify(remoteCall).send()
    }

    @Test
    fun `register eth account called`() {
        val serializedValue = "{}"
        val transactionByteArray = address.toByteArray()
        val keyPair = mock(KeyPair::class.java)
        val baseResponse = mock(BaseResponse::class.java)
        val irohaRequest = IrohaRequest(Base64.getEncoder().encodeToString(transactionByteArray))

        given(transactionFactory.buildRegisterEthRequest(srcAccountId, serializedValue, keyPair)).willReturn(Single.just(Pair(irohaRequest, address)))
        given(api.ethRegister(irohaRequest)).willReturn(Single.just(baseResponse))

        ethereumRepository.registerEthAccount(srcAccountId, serializedValue, keyPair)
            .test()
            .assertComplete()
    }

    @Test
    fun `start withdraw`() {
        val withdrawTransactionDao = mock(WithdrawTransactionDao::class.java)
        val transactionByteArray = address.toByteArray()
        val keyPair = mock(KeyPair::class.java)
        val transaction = mock(TransactionOuterClass.Transaction::class.java)
        val irohaRequest = IrohaRequest(Base64.getEncoder().encodeToString(transactionByteArray))
        given(etherWeiConverter.fromWeiToEther(gasLimit * gasPrice)).willReturn(BigDecimal(minerFee))

        given(db.withdrawTransactionDao()).willReturn(withdrawTransactionDao)
        given(transactionFactory.buildWithdrawTransaction(amount, srcAccountId, address, minerFee, keyPair)).willReturn(Single.just(Pair(irohaRequest, txHash)))
        given(api.withdraw(irohaRequest)).willReturn(Single.just(BaseResponse(StatusDto("200", "Ok"))))

        ethereumRepository.startWithdraw(amount, srcAccountId, address, minerFee, keyPair)
            .test()
            .assertComplete()
    }

    @Test
    fun `confirm withdraw`() {
        val toAddress = "toAddress"
        val txHash = Hex.toHexString("irohaTxHash1".toByteArray())
        val txHashBytes = Hex.decode(txHash)
        val userRemoteCall = RemoteCall<BigInteger>(Callable { BigInteger.ZERO })
        val v = "1c"
        val r = "aacab589047daab39a75cf52d9c9188f5e20f9ded20e0b7e7e46908168992d01"
        val s = "bacab589047daab39a75cf52d9c9188f5e20a9ded20e0b7e7e46908168992d01"
        val vList = listOf(BigInteger(Hex.decode(v)))
        val result = mock(RemoteCall::class.java)
        val transactionReceipt = mock(TransactionReceipt::class.java)
        val gasPrice = BigInteger.ONE
        val gasLimit = BigInteger.ONE

        val withdrawalProofsResponse = WithdrawalProofsResponse(StatusDto("200", "Ok"), listOf(
            WithdrawalProof(
                "id",
                100,
                1,
                1,
                "accountId",
                "tokenContractId",
                amount,
                "relay",
                txHash,
                toAddress,
                listOf(KeccakProof(v, r, s))
            ),
            WithdrawalProof(
                "id2",
                100,
                1,
                1,
                "accountId",
                "tokenContractId",
                BigDecimal.TEN,
                "relay2",
                "irohaTxHash2",
                "toId",
                listOf(KeccakProof("12", "bbcab589047daab39a75cf52d9c9188f5e20f9ded20e0b7e7e46908168992d01", "bbcab589047daab39a75cf52d9c9188f5e20f9ded20e0b7e7e46908168992d01"))
            )
        ))

        given(soranetApi.getWithdrawalProofs(srcAccountId)).willReturn(Single.just(withdrawalProofsResponse))
        given(smartContractApi.used(txHashBytes)).willReturn(userRemoteCall)
        given(
            smartContractApi.mintTokensByPeers(
                eqNonNull(amount),
                eqNonNull(toAddress),
                eqNonNull(txHashBytes),
                eqNonNull(vList),
                anyNonNull(),
                anyNonNull(),
                eqNonNull(toAddress),
                eqNonNull(xorTokenAddress)
            )
        ).willReturn(result as RemoteCall<TransactionReceipt>)
        given(result.send()).willReturn(transactionReceipt)
        given(transactionReceipt.transactionHash).willReturn(txHash)

        ethereumRepository.confirmWithdraw(ethereumCredentials, amount, txHash, srcAccountId, gasPrice, gasLimit, xorTokenAddress)
            .test()
            .assertResult(txHash)
            .assertNoErrors()

        verify(contractApiProvider).setGasPrice(gasPrice)
        verify(contractApiProvider).setGasLimit(gasLimit)

        verify(smartContractApi).mintTokensByPeers(
            eqNonNull(amount),
            eqNonNull(toAddress),
            eqNonNull(txHashBytes),
            eqNonNull(vList),
            anyNonNull(),
            anyNonNull(),
            eqNonNull(toAddress),
            eqNonNull(xorTokenAddress)
        )
    }
}