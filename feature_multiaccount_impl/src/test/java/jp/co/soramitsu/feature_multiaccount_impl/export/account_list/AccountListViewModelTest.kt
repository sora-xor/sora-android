/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.export.account_list

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.base.model.ToolbarState
import jp.co.soramitsu.common.base.model.ToolbarType
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.AccountListViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountListScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportAccountData
import jp.co.soramitsu.common.R
import jp.co.soramitsu.test_shared.MainCoroutineRule
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
                    soraAccounts.first().substrateAddress,
                    soraAccounts.first().accountName
                ),
                ExportAccountData(
                    false,
                    false,
                    drawable,
                    soraAccounts.last().substrateAddress,
                    soraAccounts.last().accountName
                ),
            )
        )

        given(multiAccInteractor.flowSoraAccountsList()).willReturn(flow { emit(soraAccounts) })
        given(multiAccInteractor.getCurrentSoraAccount()).willReturn(soraAccounts.first())
        given(avatarGenerator.createAvatar(anyString(), anyInt())).willReturn(drawable)
        given(resourceManager.getString(R.string.settings_accounts)).willReturn("Accounts")

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
        accountListViewModel.toolbarState.value?.let {
            assertEquals(
                it, ToolbarState(
                    type = ToolbarType.SMALL,
                    title = "Accounts"
                )
            )
        }

        assertEquals(expectedState, accountListViewModel.accountListScreenState.value)
    }

    @Test
    fun backPressed() {
        accountListViewModel.onToolbarNavigation()
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

        verify(multiAccInteractor).setCurSoraAccount(address)
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
        given(resourceManager.getString(R.string.common_backup)).willReturn("backup")

        val address = soraAccounts.first().substrateAddress
        accountListViewModel.onAccountSelected(address)

        accountListViewModel.accountListScreenState.value?.let {
            assertTrue(it.chooserActivated)
            assertTrue(it.accountList.first { it.address == "address" }.isSelected)
        }

        accountListViewModel.toolbarState.value?.let {
            assertEquals(
                it, ToolbarState(
                    type = ToolbarType.SMALL,
                    navIcon = R.drawable.ic_cross_red_16,
                    title = "1",
                    action = "backup",
                )
            )
        }
    }

    @Test
    fun onToolbarAction() {
        accountListViewModel.onAccountSelected(soraAccounts.first().substrateAddress)
        accountListViewModel.onAccountSelected(soraAccounts.last().substrateAddress)
        accountListViewModel.onToolbarAction()

        verify(mainRouter).showExportJSONProtection(soraAccounts.map { it.substrateAddress })
    }

    @Test
    fun onToolbarNavigationWithChooserDisabled() {
        accountListViewModel.onToolbarNavigation()

        verify(mainRouter).popBackStack()
    }

    @Test
    fun onToolbarNavigationWithChooserEnabled() {
        accountListViewModel.onAccountSelected("address")
        accountListViewModel.onToolbarNavigation()

        accountListViewModel.accountListScreenState.value?.let {
            assertFalse(it.chooserActivated)
            assertEquals(it.accountList.count { it.isSelected }, 0)
        }

        accountListViewModel.toolbarState.value?.let {
            assertEquals(
                it,
                ToolbarState(
                    type = ToolbarType.SMALL,
                    title = "Accounts"
                )
            )
        }
    }
}