/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.domain

import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.test_shared.anyNonNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class OnboardingInteractorTests {

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
    fun `getMnemonic() returns mnemonic from did repository`() = runBlockingTest {
        val mnemonic = "test mnemonic"

        given(credentialsRepository.retrieveMnemonic())
            .willReturn(mnemonic)

        assertEquals(mnemonic, interactor.getMnemonic())
        verify(credentialsRepository).retrieveMnemonic()
        verifyNoMoreInteractions(credentialsRepository)
        verifyZeroInteractions(userRepository)
    }

    @Test
    fun `getMnemonic() throws General error if mnemonic from did repo is empty`() =
        runBlockingTest {
            given(credentialsRepository.retrieveMnemonic())
                .willReturn("")
            val result = runCatching {
                interactor.getMnemonic()
            }
            assertTrue(result.isFailure && result.exceptionOrNull()!! is SoraException)
            verify(credentialsRepository).retrieveMnemonic()
            verifyNoMoreInteractions(credentialsRepository)
            verifyZeroInteractions(userRepository)
        }

    @Test
    fun `runRecoverFlow() calls recoverAccount from did repo and getUser() from user repo`() =
        runBlockingTest {
            given(credentialsRepository.retrieveMnemonic()).willReturn("mnemonic")
            given(credentialsRepository.restoreUserCredentials("mnemonic")).willReturn(Unit)
            given(userRepository.saveAccountName("")).willReturn(Unit)

            interactor.runRecoverFlow("mnemonic", "")
            verify(credentialsRepository).restoreUserCredentials("mnemonic")
            verify(credentialsRepository).retrieveMnemonic()
            verify(userRepository).saveAccountName(anyString())
            verify(userRepository).saveRegistrationState(anyNonNull())
            verifyNoMoreInteractions(credentialsRepository, userRepository)
        }
}
