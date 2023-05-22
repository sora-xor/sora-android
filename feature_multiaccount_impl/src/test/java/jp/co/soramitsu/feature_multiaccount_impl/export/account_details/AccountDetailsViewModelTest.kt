/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.export.account_details

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountDetailsScreenState
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class AccountDetailsViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var multiAccInteractor: MultiaccountInteractor

    @Mock
    private lateinit var mainRouter: MainRouter

    @Mock
    private lateinit var copy: ClipboardManager

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var accountDetailsViewModel: AccountDetailsViewModel

    private val account = SoraAccount("address", "accountName")

    @Before
    fun setUp() = runTest {
        given(multiAccInteractor.getSoraAccount(account.substrateAddress)).willReturn(account)
        given(multiAccInteractor.getMnemonic(account)).willReturn("mne mo nic")

        accountDetailsViewModel = AccountDetailsViewModel(
            multiAccInteractor,
            mainRouter,
            resourceManager,
            copy,
            account.substrateAddress,
        )
    }

    @Test
    fun init() = runTest {
        val toolbar = accountDetailsViewModel.toolbarState.getOrAwaitValue()
        assertEquals(R.string.account_options, toolbar.basic.title)
        assertTrue(toolbar.type is SoramitsuToolbarType.Small)

        val state = accountDetailsViewModel.accountDetailsScreenState.getOrAwaitValue()
        assertEquals(
            AccountDetailsScreenState(
                InputTextState(
                    value = TextFieldValue(account.accountName),
                    leadingIcon = R.drawable.ic_input_pencil_24,
                ),
                true,
                "address",
            ),
            state,
        )
    }

    @Test
    fun onShowPassphraseClicked() = runTest {
        accountDetailsViewModel.onShowPassphrase()
        verify(mainRouter).showExportPassphraseProtection(account.substrateAddress)
    }

    @Test
    fun onShowRawSeedClicked() = runTest {
        accountDetailsViewModel.onShowRawSeed()
        verify(mainRouter).showExportSeedProtection(account.substrateAddress)
    }

    @Test
    fun onLogoutCalled() = runTest {
        accountDetailsViewModel.onLogout()
        verify(mainRouter).showPinForLogout(account.substrateAddress)
    }

    @Test
    fun onNameChangeCalledTwice() = runTest {
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "F"))
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "Fo"))
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "Foo"))
        advanceTimeBy(700)
        verify(multiAccInteractor).updateName(account.substrateAddress, "Foo")
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "Foo "))
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "Foo B"))
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "Foo Ba"))
        advanceUntilIdle()
        verify(multiAccInteractor).updateName(account.substrateAddress, "Foo Ba")
    }

    @Test
    fun onNameChangeCalled() = runTest {
        val textFieldValue = TextFieldValue(text = "accountName")
        val expectedState = AccountDetailsScreenState(
            InputTextState(
                value = textFieldValue,
                leadingIcon = R.drawable.ic_input_pencil_24,
            ),
            true,
            "address",
        )

        advanceUntilIdle()
        var state = accountDetailsViewModel.accountDetailsScreenState.getOrAwaitValue()
        assertEquals(expectedState, state)

        accountDetailsViewModel.onNameChange(TextFieldValue(text = "F"))
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "Fo"))
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "Foo"))
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "Foo "))
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "Foo B"))
        accountDetailsViewModel.onNameChange(TextFieldValue(text = "Foo Ba"))
        state = accountDetailsViewModel.accountDetailsScreenState.getOrAwaitValue()
        assertEquals("Foo Ba", state.accountNameState.value.text)

        advanceUntilIdle()
        verify(multiAccInteractor).updateName(account.substrateAddress, "Foo Ba")
    }
}