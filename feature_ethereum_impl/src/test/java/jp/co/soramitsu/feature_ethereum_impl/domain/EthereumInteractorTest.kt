package jp.co.soramitsu.feature_ethereum_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.test_shared.RxSchedulersRule
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
import java.math.BigDecimal
import java.math.BigInteger
import java.security.KeyPair

@RunWith(MockitoJUnitRunner::class)
class EthereumInteractorTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var ethereumRepository: EthereumRepository
    @Mock private lateinit var didRepository: DidRepository

    private lateinit var ethereumInteractorImpl: EthereumInteractorImpl

    private val mnemonic = "mnemonic"
    private val accountId = "accountId"
    private val ethereumCredentials = EthereumCredentials(BigInteger("4309705105768215758615629237602468660061307779130899782366233796951641406004"))

    private val keyPair = mock(KeyPair::class.java)

    @Before fun setUp() {
        ethereumInteractorImpl = EthereumInteractorImpl(ethereumRepository, didRepository)
        given(didRepository.getAccountId()).willReturn(Single.just(accountId))
        given(didRepository.retrieveKeypair()).willReturn(Single.just(keyPair))
    }


    @Test fun `register eth account called`() {
        val proofs = "{}"
        val ethCredentials = EthereumCredentials(BigInteger.ZERO)

        given(didRepository.retrieveMnemonic()).willReturn(Single.just(mnemonic))
        given(ethereumRepository.getEthCredentials(mnemonic)).willReturn(Single.just(ethCredentials))
        given(ethereumRepository.getSerializedProof(ethCredentials)).willReturn(Single.just(proofs))
        given(ethereumRepository.registerEthAccount(accountId, proofs, keyPair)).willReturn(Completable.complete())

        ethereumInteractorImpl.registerEthAccount()
            .test()
            .assertComplete()
            .assertNoErrors()

        verify(ethereumRepository).registerEthAccount(accountId, proofs, keyPair)
    }

    @Test fun `start withdraw called`() {
        val amount = BigDecimal.ONE
        val minerFee = "11.0"
        val ethAddress = "0xaddress"

        given(ethereumRepository.startWithdraw(amount, accountId, ethAddress, minerFee, keyPair)).willReturn(Completable.complete())

        ethereumInteractorImpl.startWithdraw(amount, ethAddress, minerFee)
            .test()
            .assertComplete()
            .assertNoErrors()

        verify(ethereumRepository).startWithdraw(amount, accountId, ethAddress, minerFee, keyPair)
    }
}