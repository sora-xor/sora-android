/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_multiaccount_impl.export.account_list

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.AccountListViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountListScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportAccountData
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AccountListViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var multiAccInteractor: MultiaccountInteractor

    @Mock
    private lateinit var avatarGenerator: AccountAvatarGenerator

    @Mock
    private lateinit var mainRouter: MainRouter

    @Mock
    private lateinit var drawable: PictureDrawable

    @Mock
    private lateinit var clipboardManager: BasicClipboardManager

    private lateinit var accountListViewModel: AccountListViewModel

    private val soraAccounts =
        listOf(SoraAccount("address", "accountName"), SoraAccount("curAddress", "curAccountName"))

    private lateinit var expectedState: AccountListScreenState

    @Before
    fun setUp() = runTest {
        expectedState = AccountListScreenState(
            false,
            listOf(
                ExportAccountData(
                    true,
                    false,
                    drawable,
                    soraAccounts.first(),
                ),
                ExportAccountData(
                    false,
                    false,
                    drawable,
                    soraAccounts.last(),
                ),
            )
        )

        given(multiAccInteractor.flowSoraAccountsList()).willReturn(flow { emit(soraAccounts) })
        given(multiAccInteractor.flowUnavailableWalletIds()).willReturn(flow { emit(emptySet()) })
        given(multiAccInteractor.getCurrentSoraAccount()).willReturn(soraAccounts.first())
        given(avatarGenerator.createAvatar(anyString(), anyInt())).willReturn(drawable)

        accountListViewModel = AccountListViewModel(
            multiAccInteractor,
            avatarGenerator,
            mainRouter,
            clipboardManager,
        )
    }

    @Test
    fun init() = runTest {
        val s = accountListViewModel.toolbarState.getOrAwaitValue()
        assertTrue(s.type is SoramitsuToolbarType.Small)
        assertEquals(R.string.settings_accounts, s.basic.title)
        assertEquals(expectedState, accountListViewModel.accountListScreenState.value)
    }

    @Test
    fun `an account that cannot sign is labelled, and the label follows a repair`() = runTest {
        val readOnly = MutableStateFlow(setOf("address"))
        given(multiAccInteractor.flowUnavailableWalletIds()).willReturn(readOnly)
        val viewModel = AccountListViewModel(
            multiAccInteractor,
            avatarGenerator,
            mainRouter,
            clipboardManager,
        )
        advanceUntilIdle()

        val labelled = requireNotNull(viewModel.accountListScreenState.value).accountList
        assertTrue(labelled.first { it.account.substrateAddress == "address" }.readOnly)
        assertFalse(labelled.first { it.account.substrateAddress == "curAddress" }.readOnly)
        // A read-only account stays listed and selectable.
        assertEquals(2, labelled.size)

        readOnly.value = emptySet()
        advanceUntilIdle()

        assertTrue(
            requireNotNull(viewModel.accountListScreenState.value).accountList.none { it.readOnly }
        )
    }

    @Test
    fun `the list is shown without labels when they cannot be read`() = runTest {
        given(multiAccInteractor.flowUnavailableWalletIds())
            .willReturn(flow { throw IllegalStateException("WALLET_DATABASE_RECOVERY_BLOCKED") })
        val viewModel = AccountListViewModel(
            multiAccInteractor,
            avatarGenerator,
            mainRouter,
            clipboardManager,
        )
        advanceUntilIdle()

        val list = requireNotNull(viewModel.accountListScreenState.value).accountList
        assertEquals(2, list.size)
        assertTrue(list.none { it.readOnly })
        assertNull(viewModel.errorFromResourceLiveData.value)
    }

    @Test
    fun `removing the last account empties the list without asking for a selected account`() = runTest {
        // The candidate crashed here: the list emitted no accounts right after the removal and
        // the screen (still on the back stack) asked the repository for the selected account.
        val accounts = MutableStateFlow(soraAccounts)
        given(multiAccInteractor.flowSoraAccountsList()).willReturn(accounts)
        val viewModel = AccountListViewModel(
            multiAccInteractor,
            avatarGenerator,
            mainRouter,
            clipboardManager,
        )
        advanceUntilIdle()
        assertEquals(2, requireNotNull(viewModel.accountListScreenState.value).accountList.size)
        // What the repository answers once no account is left. It must not be asked.
        lenient().`when`(multiAccInteractor.getCurrentSoraAccount())
            .thenThrow(IllegalArgumentException("Required value was null."))

        accounts.value = emptyList()
        advanceUntilIdle()

        assertEquals(
            emptyList<ExportAccountData>(),
            requireNotNull(viewModel.accountListScreenState.value).accountList,
        )
        assertNull(viewModel.errorFromResourceLiveData.value)
    }

    @Test
    fun `a failure while the list is built is reported, not thrown`() = runTest {
        val accounts = MutableStateFlow(soraAccounts)
        given(multiAccInteractor.flowSoraAccountsList()).willReturn(accounts)
        val viewModel = AccountListViewModel(
            multiAccInteractor,
            avatarGenerator,
            mainRouter,
            clipboardManager,
        )
        advanceUntilIdle()
        given(multiAccInteractor.getCurrentSoraAccount())
            .willThrow(IllegalStateException("WALLET_DATABASE_RECOVERY_BLOCKED"))

        accounts.value = listOf(soraAccounts.first())
        advanceUntilIdle()

        assertEquals(
            R.string.failure_generic_title to R.string.failure_generic_message,
            viewModel.errorFromResourceLiveData.value,
        )
    }

    @Test
    fun backPressed() {
        accountListViewModel.onBackPressed()
        verify(mainRouter).popBackStack()
    }

    @Test
    fun onAccountOptionsClicked() {
        val address = "address"
        accountListViewModel.onAccountOptionsClicked(address)

        verify(mainRouter).showAccountDetails(address)
    }

    @Test
    fun onAccountClicked() = runTest {
        val address = "address"

        accountListViewModel.onAccountClicked(address)
        advanceUntilIdle()

        verify(multiAccInteractor).setCurSoraAccount(soraAccounts.first())
        verify(mainRouter).popBackStack()
    }

    @Test
    fun onAccountLongClicked() {
        val address = "address"

        accountListViewModel.onAccountLongClicked(address)

        verify(clipboardManager).addToClipboard(address)
    }

    @Test
    fun onAccountSelectedClicked() {
        val address = soraAccounts.first().substrateAddress
        accountListViewModel.onAccountSelected(address)

        accountListViewModel.accountListScreenState.value?.let {
            assertTrue(it.isActionMode)
            assertTrue(it.accountList.first { it.account.substrateAddress == "address" }.isSelectedAction)
        }

        val s = accountListViewModel.toolbarState.getOrAwaitValue()
        assertEquals("1", s.basic.title)
        assertEquals(R.string.common_backup, s.basic.actionLabel)
    }

    @Test
    fun onToolbarAction() {
        accountListViewModel.onAccountSelected(soraAccounts.first().substrateAddress)
        accountListViewModel.onAccountSelected(soraAccounts.last().substrateAddress)
        accountListViewModel.onAction()

        verify(mainRouter).showExportJSONProtection(soraAccounts.map { it.substrateAddress })
    }

    @Test
    fun onToolbarNavigationWithChooserDisabled() {
        accountListViewModel.onBackPressed()

        verify(mainRouter).popBackStack()
    }

    @Test
    fun onToolbarNavigationWithChooserEnabled() {
        accountListViewModel.onAccountSelected("address")
        accountListViewModel.onBackPressed()

        accountListViewModel.accountListScreenState.value?.let {
            assertFalse(it.isActionMode)
            assertEquals(it.accountList.count { it.isSelectedAction }, 0)
        }

        val s = accountListViewModel.toolbarState.getOrAwaitValue()
        assertEquals(R.string.settings_accounts, s.basic.title)
    }
}
