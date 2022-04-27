/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.mnemonic

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
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
class MnemoinicViewModelTest {

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
    private lateinit var preloader: WithPreloader

    private lateinit var mnemonicViewModel: jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic.MnemonicViewModel

    @Before
    fun setUp() {
        mnemonicViewModel =
            jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic.MnemonicViewModel(
                interactor,
                router,
                preloader
            )
    }

    @Test
    fun `btn next clicked`() {
        mnemonicViewModel.btnNextClicked()
//        verify(router).showMnemonicConfirmation()
    }

    @Test
    fun `get passphrase called`() = runBlockingTest {
        val mnemonic = listOf(
            "mnemonic",
            "qwerty",
            "zxcasd"
        )
        given(interactor.getMnemonic()).willReturn("mnemonic qwerty zxcasd")
        mnemonicViewModel.getPassphrase()
        assertEquals(mnemonicViewModel.mnemonicWords.value, mnemonic)
    }
}