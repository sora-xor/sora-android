/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.AppVersion
import jp.co.soramitsu.feature_account_api.domain.model.Country
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserCreatingCase
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigInteger

@RunWith(MockitoJUnitRunner::class)
class OnboardingInteractorTests {

    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var didRepository: DidRepository
    @Mock private lateinit var ethereumRepository: EthereumRepository

    private lateinit var interactor: OnboardingInteractor
    val ethereumCredentials = EthereumCredentials(BigInteger.ONE)

    @Before fun setUp() {
        given(ethereumRepository.getEthCredentials(anyString())).willReturn(Single.just(ethereumCredentials))
        interactor = OnboardingInteractor(userRepository, didRepository, ethereumRepository)
    }

    @Test fun `getMnemonic() returns mnemonic from did repository`() {
        val mnemonic = "test mnemonic"

        given(didRepository.retrieveMnemonic())
            .willReturn(Single.just(mnemonic))

        interactor.getMnemonic().test()
            .assertValue(mnemonic)
            .assertComplete()
            .assertNoErrors()

        verify(didRepository).retrieveMnemonic()
        verifyNoMoreInteractions(didRepository)
        verifyZeroInteractions(userRepository)
    }

    @Test fun `getMnemonic() throws General error if mnemonic from did repo is empty`() {
        given(didRepository.retrieveMnemonic())
            .willReturn(Single.just(""))

        interactor.getMnemonic().test()
            .assertError { it is SoraException && it.kind == SoraException.Kind.BUSINESS && ResponseCode.GENERAL_ERROR == it.errorResponseCode }

        verify(didRepository).retrieveMnemonic()
        verifyNoMoreInteractions(didRepository)
        verifyZeroInteractions(userRepository)
    }

    @Test fun `runRegisterFlow() checks version, then register ddo and check invite code`() {
        val appVersion = AppVersion(true, "")

        given(userRepository.checkAppVersion()).willReturn(Single.just(appVersion))
        given(didRepository.registerUserDdo()).willReturn(Completable.complete())
        given(didRepository.retrieveMnemonic()).willReturn(Single.just(""), Single.just("mnemonic"))
        given(userRepository.checkInviteCodeAvailable()).willReturn(Completable.complete())

        interactor.runRegisterFlow()
            .test()
            .assertValue(appVersion)
            .assertComplete()
            .assertNoErrors()

        verify(userRepository).checkAppVersion()
        verify(didRepository).registerUserDdo()
        verify(didRepository, times(2)).retrieveMnemonic()
        verify(userRepository).checkInviteCodeAvailable()
        verifyNoMoreInteractions(userRepository, didRepository)
    }

    @Test fun `checkVersionIsSupported calls user repo checkAppVersion()`() {
        val appVersion = AppVersion(true, "")
        given(userRepository.checkAppVersion()).willReturn(Single.just(appVersion))

        interactor.checkVersionIsSupported().test()
            .assertValue(appVersion)
            .assertComplete()
            .assertNoErrors()

        verify(userRepository).checkAppVersion()
        verifyNoMoreInteractions(userRepository)
        verifyZeroInteractions(didRepository)
    }

    @Test fun `runRecoverFlow() calls recoverAccount from did repo and getUser() from user repo`() {
        given(didRepository.recoverAccount(anyString())).willReturn(Completable.complete())
        given(didRepository.retrieveMnemonic()).willReturn(Single.just("mnemonic"))
        given(userRepository.getUser(anyBoolean())).willReturn(Single.just(mock(User::class.java)))

        interactor.runRecoverFlow("")
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(didRepository).recoverAccount(anyString())
        verify(didRepository).retrieveMnemonic()
        verify(userRepository).getUser(anyBoolean())
        verify(userRepository).saveRegistrationState(anyNonNull())
        verifyNoMoreInteractions(didRepository, userRepository)
    }

    @Test fun `requestNewCode() calls requestSMSCode() from user repo`() {
        val timeLeft = 1

        given(userRepository.requestSMSCode()).willReturn(Single.just(timeLeft))

        interactor.requestNewCode().test()
            .assertComplete()
            .assertNoErrors()
            .assertValue(timeLeft)

        verify(userRepository).requestSMSCode()
        verifyNoMoreInteractions(userRepository)
        verifyZeroInteractions(didRepository)
    }

    @Test fun `verifySmsCode() calls verifySMSCode() from user repo`() {
        given(userRepository.verifySMSCode(anyString())).willReturn(Completable.complete())

        interactor.verifySmsCode("").test()
            .assertComplete()
            .assertNoErrors()

        verify(userRepository).verifySMSCode(anyString())
        verifyNoMoreInteractions(userRepository)
        verifyZeroInteractions(didRepository)
    }

    @Test fun `getCountries() calls getAllCountries() from user repo`() {
        val countries = mutableListOf<Country>()

        given(userRepository.getAllCountries()).willReturn(Single.just(countries))

        interactor.getCountries().test()
            .assertComplete()
            .assertNoErrors()
            .assertValue(countries)

        verify(userRepository).getAllCountries()
        verifyNoMoreInteractions(userRepository)
        verifyZeroInteractions(didRepository)
    }

    @Test fun `createUser() calls createUser() from user repo`() {
        val createUserCase = UserCreatingCase(true, 5)

        given(userRepository.createUser(anyString())).willReturn(Single.just(createUserCase))

        interactor.createUser("").test()
            .assertComplete()
            .assertNoErrors()
            .assertValue(createUserCase)

        verify(userRepository).createUser(anyString())
        verifyNoMoreInteractions(userRepository)
        verifyZeroInteractions(didRepository)
    }

    @Test fun `register() with correct invite code`() {
        given(userRepository.register(anyString(), anyString(), anyString(), anyString()))
            .willReturn(Single.just(true))

        given(userRepository.getUser(anyBoolean())).willReturn(Single.just(mock(User::class.java)))

        interactor.register("", "", "", "").test()
            .assertComplete()
            .assertNoErrors()

        verify(userRepository).register(anyString(), anyString(), anyString(), anyString())
        verify(userRepository).getUser(anyBoolean())
        verifyNoMoreInteractions(userRepository)
        verifyZeroInteractions(didRepository)
    }

    @Test fun `register() with incorrect invite code`() {
        given(userRepository.register(anyString(), anyString(), anyString(), anyString()))
            .willReturn(Single.just(false))

        interactor.register("", "", "", "").test()
            .assertComplete()
            .assertNoErrors()

        verify(userRepository).register(anyString(), anyString(), anyString(), anyString())
        verifyNoMoreInteractions(userRepository)
        verifyZeroInteractions(didRepository)
    }

    @Test fun `getParentInviteCode() calls getParentInviteCode() from user repo`() {
        val inviteCode = "test invite code"
        given(userRepository.getParentInviteCode()).willReturn(Single.just(inviteCode))

        interactor.getParentInviteCode().test()
            .assertComplete()
            .assertNoErrors()
            .assertValue(inviteCode)

        verify(userRepository).getParentInviteCode()
        verifyNoMoreInteractions(userRepository)
        verifyZeroInteractions(didRepository)
    }

    @Test fun `changePersonalData calls userRepository saveRegistrationState`() {
        interactor.changePersonalData()
            .test()
            .assertComplete()
            .assertNoErrors()

        verify(userRepository).saveRegistrationState(OnboardingState.INITIAL)
    }
}