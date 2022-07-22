/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.recovery

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import jp.co.soramitsu.feature_multiaccount_impl.presentation.recovery.RecoveryViewModel
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class RecoveryViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: MultiaccountInteractor

    @Mock
    private lateinit var router: MultiaccountRouter

    @Mock
    private lateinit var progress: WithProgress

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var recoveryViewModel: RecoveryViewModel

    @Before
    fun setUp() {
        recoveryViewModel =
            RecoveryViewModel(
                interactor,
                router,
                resourceManager,
                progress
            )
    }

    @Test
    fun `btn next clicked`() = runTest {
        val mnemonic = "faculty soda zero quote reopen rubber jazz feed casual shed veteran badge"
        val soraAccount = mock(SoraAccount::class.java)

        given(interactor.recoverSoraAccountFromMnemonic(mnemonic, "")).willReturn(soraAccount)
        given(interactor.continueRecoverFlow(soraAccount)).willReturn(Unit)
        given(interactor.isMnemonicValid(mnemonic)).willReturn(true)

        recoveryViewModel.btnNextClick(mnemonic, "")
        advanceUntilIdle()
        verify(progress).showProgress()
        verify(progress).hideProgress()
    }

    @Test
    fun `next button click with multiAccount EXPECT showMainScreen true`() = runTest {
        val mnemonic = "faculty soda zero quote reopen rubber jazz feed casual shed veteran badge"
        val soraAccount = mock(SoraAccount::class.java)

        given(interactor.recoverSoraAccountFromMnemonic(mnemonic, "")).willReturn(soraAccount)
        given(interactor.continueRecoverFlow(soraAccount)).willReturn(Unit)
        given(interactor.isMnemonicValid(mnemonic)).willReturn(true)
        given(interactor.isMultiAccount()).willReturn(true)

        recoveryViewModel.btnNextClick(mnemonic, "")
        advanceUntilIdle()

        assertTrue(recoveryViewModel.showMainScreen.value!!)
    }

    @Test
    fun `next button click without multiAccount EXPECT showMainScreen true`() = runTest {
        val mnemonic = "faculty soda zero quote reopen rubber jazz feed casual shed veteran badge"
        val soraAccount = mock(SoraAccount::class.java)

        given(interactor.recoverSoraAccountFromMnemonic(mnemonic, "")).willReturn(soraAccount)
        given(interactor.continueRecoverFlow(soraAccount)).willReturn(Unit)
        given(interactor.isMnemonicValid(mnemonic)).willReturn(true)
        given(interactor.isMultiAccount()).willReturn(false)

        recoveryViewModel.btnNextClick(mnemonic, "")
        advanceUntilIdle()

        assertFalse(recoveryViewModel.showMainScreen.value!!)
    }
}