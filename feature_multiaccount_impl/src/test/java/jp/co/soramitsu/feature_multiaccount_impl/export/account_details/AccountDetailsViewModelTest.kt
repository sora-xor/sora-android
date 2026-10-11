/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_multiaccount_impl.export.account_details

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavController
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsViewModel
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletOptions
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletReason
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ReadOnlyAccountUi
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountDetailsScreenState
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import jp.co.soramitsu.xbackup.BackupService
import jp.co.soramitsu.xbackup.domain.models.BackupAccountType
import jp.co.soramitsu.xbackup.domain.models.CryptoType
import jp.co.soramitsu.xbackup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.xbackup.domain.models.Json
import jp.co.soramitsu.xbackup.domain.models.Seed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verifyNoMoreInteractions

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
    private lateinit var copy: BasicClipboardManager

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var backupService: BackupService

    @Mock
    private lateinit var navController: NavController

    @Mock
    private lateinit var accountResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var accountDetailsViewModel: AccountDetailsViewModel

    private val account = SoraAccount("address", "accountName")

    /** The accounts that cannot sign on this phone, as the repository reports them. */
    private val unavailable = MutableStateFlow<Set<String>>(emptySet())

    private fun readOnlyOptions(
        reason: UnavailableWalletReason = UnavailableWalletReason.KEY_NOT_USABLE,
        canShowPhraseAfterPin: Boolean = true,
    ) = UnavailableWalletOptions(
        walletId = account.substrateAddress,
        canRepairWithPhrase = true,
        canRemoveAndImport = true,
        canShowPhraseAfterPin = canShowPhraseAfterPin,
        showPhraseBeforeRemoval = canShowPhraseAfterPin,
        reason = reason,
    )

    private fun viewModel(cloudBackup: Boolean = true) = AccountDetailsViewModel(
        multiAccInteractor,
        mainRouter,
        resourceManager,
        copy,
        jp.co.soramitsu.common.backup.CloudBackupProvider(cloudBackup) { backupService },
        coroutineManager,
        account.substrateAddress,
    )

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        given(multiAccInteractor.getSoraAccount(account.substrateAddress)).willReturn(account)
        given(multiAccInteractor.getMnemonic(account)).willReturn("mne mo nic")
        given(multiAccInteractor.flowUnavailableWalletIds()).willReturn(unavailable)
        given(coroutineManager.io).willReturn(this.coroutineContext[CoroutineDispatcher])
        given(coroutineManager.main).willReturn(Dispatchers.Main)
        given(backupService.isAccountBackedUp("address")).willReturn(true)

        accountDetailsViewModel = AccountDetailsViewModel(
            multiAccInteractor,
            mainRouter,
            resourceManager,
            copy,
            jp.co.soramitsu.common.backup.CloudBackupProvider(true) { backupService },
            coroutineManager,
            account.substrateAddress,
        )
    }

    @Test
    fun `optional backup lookup failure leaves local account details available`() = runTest {
        given(backupService.isAccountBackedUp("address")).willAnswer { throw IllegalStateException("Google unavailable") }
        val viewModel = AccountDetailsViewModel(multiAccInteractor, mainRouter, resourceManager, copy,
            jp.co.soramitsu.common.backup.CloudBackupProvider(true) { backupService }, coroutineManager, account.substrateAddress)
        testScheduler.advanceUntilIdle()
        assertEquals(account.substrateAddress, viewModel.accountDetailsScreenState.value?.address)
        assertEquals(true, viewModel.accountDetailsScreenState.value?.isMnemonicAvailable)
        org.junit.Assert.assertNull(viewModel.accountDetailsScreenState.value?.isBackupAvailable)
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
                false,
                true,
                "address"
            ),
            state,
        )
    }

    @Test
    fun `an account that signs has no read-only panel`() = runTest {
        advanceUntilIdle()

        assertNull(accountDetailsViewModel.accountDetailsScreenState.value?.readOnly)
        // Nothing to restore: the actions of the panel do nothing.
        accountDetailsViewModel.onRestoreKeysWithPhrase()
        accountDetailsViewModel.onRestoreKeysFromGoogle()
        assertNull(accountDetailsViewModel.restoreKeysEvent.value)
    }

    @Test
    fun `a read-only account shows why and what can be done`() = runTest {
        unavailable.value = setOf(account.substrateAddress)
        given(multiAccInteractor.unavailableWalletOptions(account.substrateAddress))
            .willReturn(readOnlyOptions(UnavailableWalletReason.KEY_UNREADABLE))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            ReadOnlyAccountUi(
                reason = UnavailableWalletReason.KEY_UNREADABLE,
                canRestoreWithPhrase = true,
                canRestoreFromGoogle = true,
                canShowBackup = true,
                canRemove = true,
            ),
            viewModel.accountDetailsScreenState.value?.readOnly,
        )
        // The rest of the screen is as for any account.
        assertEquals(account.substrateAddress, viewModel.accountDetailsScreenState.value?.address)
        assertEquals(true, viewModel.accountDetailsScreenState.value?.isMnemonicAvailable)
    }

    @Test
    fun `restore from Google Drive is not offered where Google backup is unavailable`() = runTest {
        unavailable.value = setOf(account.substrateAddress)
        given(multiAccInteractor.unavailableWalletOptions(account.substrateAddress))
            .willReturn(readOnlyOptions())

        val viewModel = viewModel(cloudBackup = false)
        advanceUntilIdle()

        val panel = requireNotNull(viewModel.accountDetailsScreenState.value?.readOnly)
        assertTrue(panel.canRestoreWithPhrase)
        assertFalse(panel.canRestoreFromGoogle)
        viewModel.onRestoreKeysFromGoogle()
        assertNull(viewModel.restoreKeysEvent.value)
    }

    @Test
    fun `restoring keys opens Import account at the chosen step and keeps the account`() = runTest {
        unavailable.value = setOf(account.substrateAddress)
        given(multiAccInteractor.unavailableWalletOptions(account.substrateAddress))
            .willReturn(readOnlyOptions())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRestoreKeysWithPhrase()
        assertEquals(AccountDetailsViewModel.RestoreKeys.WITH_PHRASE, viewModel.restoreKeysEvent.value)

        viewModel.onRestoreKeysFromGoogle()
        assertEquals(AccountDetailsViewModel.RestoreKeys.FROM_GOOGLE, viewModel.restoreKeysEvent.value)

        // Neither is a removal.
        verify(mainRouter, never()).showPinForLogout(account.substrateAddress)
    }

    @Test
    fun `the saved backup of a read-only account is shown after the PIN, phrase first`() = runTest {
        unavailable.value = setOf(account.substrateAddress)
        given(multiAccInteractor.unavailableWalletOptions(account.substrateAddress))
            .willReturn(readOnlyOptions())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onShowSavedBackup()

        verify(mainRouter).showExportPassphraseProtection(account.substrateAddress)
    }

    @Test
    fun `without a matching phrase the saved backup opens the raw seed screen`() = runTest {
        unavailable.value = setOf(account.substrateAddress)
        given(multiAccInteractor.getMnemonic(account)).willReturn("")
        given(multiAccInteractor.unavailableWalletOptions(account.substrateAddress))
            .willReturn(readOnlyOptions())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onShowSavedBackup()

        verify(mainRouter).showExportSeedProtection(account.substrateAddress)
    }

    @Test
    fun `the panel goes away as soon as the account's keys are restored`() = runTest {
        unavailable.value = setOf(account.substrateAddress)
        given(multiAccInteractor.unavailableWalletOptions(account.substrateAddress))
            .willReturn(readOnlyOptions())
        val viewModel = viewModel()
        advanceUntilIdle()
        assertNotNull(viewModel.accountDetailsScreenState.value?.readOnly)

        unavailable.value = emptySet()
        advanceUntilIdle()

        assertNull(viewModel.accountDetailsScreenState.value?.readOnly)
    }

    @Test
    fun `removing a read-only account goes through the PIN and the removal screen`() = runTest {
        unavailable.value = setOf(account.substrateAddress)
        given(multiAccInteractor.unavailableWalletOptions(account.substrateAddress))
            .willReturn(readOnlyOptions())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onLogout()

        verify(mainRouter).showPinForLogout(account.substrateAddress)
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
            false,
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

    @Test
    fun `onBackupPasswordClicked() called`() = runTest {
        val password = "password"
        given(multiAccInteractor.getMnemonic(account.substrateAddress)).willReturn("mne mo nic")
        given(multiAccInteractor.getBackupMaterial(account)).willReturn(
            WalletBackupMaterial(
                canSign = true,
                phraseWords = listOf("mne", "mo", "nic"),
                seedHex = "",
                storedPhraseDoesNotMatch = false,
                storedSeedDoesNotMatch = false,
            )
        )
        given(backupService.authorize(accountResultLauncher)).willReturn(true)
        given(backupService.isAccountBackedUp(account.substrateAddress)).willReturn(false)

        accountDetailsViewModel.onBackupClicked(accountResultLauncher)
        advanceUntilIdle()
        accountDetailsViewModel.onBackupPasswordChanged(TextFieldValue(password))
        accountDetailsViewModel.onBackupPasswordConfirmationChanged(TextFieldValue(password))

        given(backupService.isAccountBackedUp(account.substrateAddress)).willReturn(true)

        val decryptedBackupAccount =
            DecryptedBackupAccount(
                name = "accountName",
                address = "address",
                mnemonicPhrase = "mne mo nic",
                cryptoType = CryptoType.SR25519,
                backupAccountType = listOf(
                    BackupAccountType.PASSPHRASE,
                ),
                seed = Seed(),
                json = Json()
            )

        accountDetailsViewModel.onBackupPasswordClicked()
        advanceUntilIdle()

        verify(backupService).saveBackupAccount(decryptedBackupAccount, password)
        val popEvent = accountDetailsViewModel.navigationPop.getOrAwaitValue()
        assertEquals(Unit, popEvent)
        assertTrue(
            accountDetailsViewModel.accountDetailsScreenState.value?.isBackupAvailable ?: false
        )
    }

    @Test
    fun `onBackupClicked() called when authorize fails`() = runTest {
        given(backupService.authorize(accountResultLauncher)).willReturn(false)

        accountDetailsViewModel.onBackupClicked(accountResultLauncher)
        advanceUntilIdle()

        verify(backupService).authorize(accountResultLauncher)
        verify(backupService).isAccountBackedUp(account.substrateAddress)
        verifyNoMoreInteractions(backupService)
    }

    @Test
    fun `onBackupClicked() called when backup exists`() = runTest {
        given(backupService.authorize(accountResultLauncher)).willReturn(true)
        given(backupService.isAccountBackedUp(account.substrateAddress)).willReturn(true)

        accountDetailsViewModel.onBackupClicked(accountResultLauncher)
        advanceUntilIdle()

        assertTrue(accountDetailsViewModel.deleteDialogState.value ?: false)
    }

    @Test
    fun `onBackupClicked() called when backup doesn't exists`() = runTest {
        given(backupService.authorize(accountResultLauncher)).willReturn(true)
        given(backupService.isAccountBackedUp(account.substrateAddress)).willReturn(false)

        accountDetailsViewModel.onBackupClicked(accountResultLauncher)
        advanceUntilIdle()

        val navEvent = accountDetailsViewModel.navEvent.getOrAwaitValue()
        assertEquals("jp.co.soramitsu.feature_multiaccount_impl.BackupAccount", navEvent.first)
    }
}
