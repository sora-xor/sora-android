/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.export.account_list

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.AccountListViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountListScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportAccountData
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private lateinit var clipboardManager: ClipboardManager

    @Mock
    private lateinit var resourceManager: ResourceManager

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
        given(multiAccInteractor.getCurrentSoraAccount()).willReturn(soraAccounts.first())
        given(avatarGenerator.createAvatar(anyString(), anyInt())).willReturn(drawable)

        accountListViewModel = AccountListViewModel(
            multiAccInteractor,
            avatarGenerator,
            mainRouter,
            clipboardManager,
            resourceManager
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

        verify(clipboardManager).addToClipboard("address", address)
        assertEquals(accountListViewModel.copiedAddressEvent.value, Unit)
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