package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.AppVersion
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyBoolean
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigInteger

@RunWith(MockitoJUnitRunner::class)
class PinCodeInteractorTest {

    @Rule @JvmField var schedulersRule = RxSchedulersRule()

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var didRepository: DidRepository
    @Mock private lateinit var ethereumRepository: EthereumRepository

    private lateinit var interactor: PinCodeInteractor
    private val pin = "1234"
    private val user = User(
        "id",
        "firstname",
        "lastname",
        "phone",
        "status",
        "parentid",
        "country",
        0,
        UserValues("", "")
    )

    @Before fun setUp() {
        given(userRepository.retrievePin()).willReturn(pin)
        interactor = PinCodeInteractor(userRepository, didRepository, ethereumRepository)
    }

    @Test fun `save pin called`() {
        interactor.savePin(pin)
            .test()
            .assertComplete()
            .assertNoErrors()

        verify(userRepository).savePin(pin)
    }

    @Test fun `check pin called`() {
        interactor.checkPin(pin)
            .test()
            .assertComplete()
            .assertNoErrors()
    }

    @Test fun `check pin called with wrong pin`() {
        interactor.checkPin("1111")
            .test()
            .assertError(SoraException::class.java)
            .assertErrorMessage(ResponseCode.WRONG_PIN_CODE.toString())
    }

    @Test fun `is pin set called`() {
        interactor.isCodeSet()
            .test()
            .assertResult(true)
    }

    @Test fun `is pin set called without setting`() {
        given(userRepository.retrievePin()).willReturn("")

        interactor.isCodeSet()
            .test()
            .assertResult(false)
    }

    @Test fun `reset user called`() {
        interactor.resetUser()

        verify(userRepository).clearUserData()
    }

    @Test fun `run check user flow called`() {
        val appVersion = AppVersion(false, "https://downloadLink")
        given(userRepository.checkAppVersion()).willReturn(Single.just(appVersion))

        interactor.runCheckUserFlow()
            .test()
            .assertResult(appVersion)
            .assertNoErrors()
    }

    @Test fun `run check user flow called false`() {
        val appVersion = AppVersion(true, "https://downloadLink")
        val ethereumCredentials = EthereumCredentials(BigInteger.ONE)
        given(userRepository.checkAppVersion()).willReturn(Single.just(appVersion))
        given(userRepository.getUser(anyBoolean())).willReturn(Single.just(user))
        given(didRepository.retrieveMnemonic()).willReturn(Single.just("mnemonic"))
        given(didRepository.retrieveUserDdo(anyNonNull())).willReturn(Completable.complete())
        given(ethereumRepository.getEthCredentials(anyNonNull())).willReturn(Single.just(ethereumCredentials))

        interactor.runCheckUserFlow()
            .test()
            .assertResult(appVersion)
            .assertNoErrors()

        verify(didRepository).retrieveMnemonic()
        verify(didRepository).retrieveUserDdo(anyNonNull())
        verify(userRepository).getUser(anyBoolean())
        verify(ethereumRepository).getEthCredentials("mnemonic")
    }
}