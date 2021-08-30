package jp.co.soramitsu.feature_ethereum_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
@Ignore("temp. will be enabled after ethereum bridge is done")
class EthereumInteractorTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var ethereumRepository: EthereumRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var healthChecker: HealthChecker

    private lateinit var ethereumInteractorImpl: EthereumInteractorImpl

    private val accountId = "accountId"

    private val keyPair = mock(Keypair::class.java)

    @Before
    fun setUp() {
        ethereumInteractorImpl =
            EthereumInteractorImpl(ethereumRepository, credentialsRepository, healthChecker)
        given(credentialsRepository.getAddress()).willReturn(accountId)
        //given(credentialsRepository.retrieveKeyPair()).willReturn(Single.just(keyPair))
    }

    @Test
    fun `start withdraw called`() {
        val amount = BigDecimal.ONE
        val minerFee = "11.0"
        val ethAddress = "0xaddress"

        //given(ethereumRepository.startWithdraw(amount, accountId, ethAddress, minerFee, keyPair)).willReturn(Completable.complete())

        ethereumInteractorImpl.startWithdraw(amount, ethAddress, minerFee)
        //verify(ethereumRepository).startWithdraw(amount, accountId, ethAddress, minerFee, keyPair)
    }
}