/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class OnboardingInteractorTests {

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var ethereumRepository: EthereumRepository

    private lateinit var interactor: OnboardingInteractor

    @Before
    fun setUp() {
        interactor = OnboardingInteractor(
            userRepository,
            credentialsRepository,
            ethereumRepository,
        )
    }

    @Test
    fun `getMnemonic() returns mnemonic from did repository`() {
        val mnemonic = "test mnemonic"

        given(credentialsRepository.retrieveMnemonic())
            .willReturn(Single.just(mnemonic))

        interactor.getMnemonic().test()
            .assertValue(mnemonic)
            .assertComplete()
            .assertNoErrors()

        verify(credentialsRepository).retrieveMnemonic()
        verifyNoMoreInteractions(credentialsRepository)
        verifyZeroInteractions(userRepository)
    }

    @Test
    fun `getMnemonic() throws General error if mnemonic from did repo is empty`() {
        given(credentialsRepository.retrieveMnemonic())
            .willReturn(Single.just(""))

        interactor.getMnemonic().test()
            .assertError { it is SoraException && it.kind == SoraException.Kind.BUSINESS && ResponseCode.GENERAL_ERROR == it.errorResponseCode }

        verify(credentialsRepository).retrieveMnemonic()
        verifyNoMoreInteractions(credentialsRepository)
        verifyZeroInteractions(userRepository)
    }

    @Test
    fun `runRecoverFlow() calls recoverAccount from did repo and getUser() from user repo`() {
        given(credentialsRepository.retrieveMnemonic()).willReturn(Single.just("mnemonic"))
        given(credentialsRepository.restoreUserCredentials("mnemonic")).willReturn(Completable.complete())
        given(userRepository.saveAccountName("")).willReturn(Completable.complete())

        interactor.runRecoverFlow("mnemonic", "")
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(credentialsRepository).restoreUserCredentials("mnemonic")
        verify(credentialsRepository).retrieveMnemonic()
        verify(userRepository).saveAccountName(anyString())
        verify(userRepository).saveRegistrationState(anyNonNull())
        verifyNoMoreInteractions(credentialsRepository, userRepository)
    }
}
