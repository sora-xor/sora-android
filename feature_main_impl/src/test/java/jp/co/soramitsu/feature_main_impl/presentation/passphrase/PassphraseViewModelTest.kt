/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.passphrase

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PassphraseViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    lateinit var interactor: MainInteractor

    private lateinit var passphraseViewModel: PassphraseViewModel

    @Before
    fun setUp() {
        passphraseViewModel = PassphraseViewModel(interactor)
    }

    @Test
    fun `get passphrase`() = runTest {
        val mnemonic = "mnemonic1 mnemonic2"
        given(interactor.getMnemonic()).willReturn(mnemonic)

        passphraseViewModel.getPassphrase()
        advanceUntilIdle()
        val r = passphraseViewModel.mnemonicWords.getOrAwaitValue()
        assertEquals(mnemonic.split(" "), r)
    }
}