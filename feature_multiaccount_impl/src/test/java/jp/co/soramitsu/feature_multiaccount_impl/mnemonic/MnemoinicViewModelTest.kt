/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.mnemonic

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.navigation.NavController
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic.MnemonicViewModel
import jp.co.soramitsu.test_shared.MainCoroutineRule
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
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MnemoinicViewModelTest {

    private companion object {

        const val ACCOUNT_NAME = "accountName"
        val SORA_ACCOUNT = SoraAccount(substrateAddress = "address", accountName = ACCOUNT_NAME)
    }

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
    private lateinit var nav: NavController

    private lateinit var mnemonicViewModel: MnemonicViewModel

    @Before
    fun setUp() {
        mnemonicViewModel =
            MnemonicViewModel(
                interactor,
                router,
                ACCOUNT_NAME
            )
    }

    @Test
    fun `btn next clicked`() = runTest {
        given(interactor.generateUserCredentials(ACCOUNT_NAME)).willReturn(SORA_ACCOUNT)
        given(interactor.getMnemonic(SORA_ACCOUNT)).willReturn("mnemonic qwerty zxcasd")

        mnemonicViewModel.getPassphrase()
        advanceUntilIdle()
        mnemonicViewModel.btnNextClicked(nav)

        verify(router).showMnemonicConfirmation(nav, SORA_ACCOUNT)
    }

    @Test
    fun `get passphrase called`() = runTest {
        val mnemonic = listOf(
            "mnemonic",
            "qwerty",
            "zxcasd"
        )
        given(interactor.generateUserCredentials(ACCOUNT_NAME)).willReturn(SORA_ACCOUNT)
        given(interactor.getMnemonic(SORA_ACCOUNT)).willReturn("mnemonic qwerty zxcasd")

        mnemonicViewModel.getPassphrase()
        advanceUntilIdle()

        assertEquals(mnemonicViewModel.mnemonicWords.value, mnemonic)
    }
}