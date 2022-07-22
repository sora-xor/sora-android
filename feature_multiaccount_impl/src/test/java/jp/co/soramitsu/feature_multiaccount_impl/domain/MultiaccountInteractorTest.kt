/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MultiaccountInteractorTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()


    private lateinit var multiaccountInteractor: MultiaccountInteractor

    private val userRepository = mock(UserRepository::class.java)
    private val credentialsRepository = mock(CredentialsRepository::class.java)

    @Before
    fun setup() {
        multiaccountInteractor = MultiaccountInteractor(userRepository, credentialsRepository)
    }

    @Test
    fun `is mnemonic valid called`() = runTest {
        val mnemonic = "mnemonic"

        multiaccountInteractor.isMnemonicValid(mnemonic)

        verify(credentialsRepository).isMnemonicValid(mnemonic)
    }

    @Test
    fun `is raw seed valid called`() = runTest {
        val seed = "seed"

        multiaccountInteractor.isRawSeedValid(seed)

        verify(credentialsRepository).isRawSeedValid(seed)
    }

    @Test
    fun `continueRecoverFlow is called`() = runTest {
        val account = mock(SoraAccount::class.java)
        multiaccountInteractor.continueRecoverFlow(account)

        verify(userRepository).insertSoraAccount(account)
        verify(userRepository).setCurSoraAccount(account)
        verify(userRepository).saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    @Test
    fun `recoverSoraAccountFromMnemonic is called`() = runTest {
        val mnemonic = "mnemonic"
        val accountName = "accountName"

        multiaccountInteractor.recoverSoraAccountFromMnemonic(mnemonic, accountName)

        verify(credentialsRepository).restoreUserCredentialsFromMnemonic(mnemonic, accountName)
    }

    @Test
    fun `recoverSoraAccountFromRawSeed is called`() = runTest {
        val rawSeed = "seed"
        val accountName = "accountName"

        multiaccountInteractor.recoverSoraAccountFromRawSeed(rawSeed, accountName)

        verify(credentialsRepository).restoreUserCredentialsFromRawSeed(rawSeed, accountName)
    }

    @Test
    fun `createUser is called`() = runTest {
        val account = mock(SoraAccount::class.java)

        multiaccountInteractor.createUser(account)

        verify(userRepository).insertSoraAccount(account)
        verify(userRepository).setCurSoraAccount(account)
        verify(userRepository).saveRegistrationState(OnboardingState.INITIAL)
        verify(userRepository).saveNeedsMigration(false, account)
        verify(userRepository).saveIsMigrationFetched(true, account)
    }

    @Test
    fun `saveRegistrationStateFinished is called`() = runTest {
        multiaccountInteractor.saveRegistrationStateFinished()

        verify(userRepository).saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }
}