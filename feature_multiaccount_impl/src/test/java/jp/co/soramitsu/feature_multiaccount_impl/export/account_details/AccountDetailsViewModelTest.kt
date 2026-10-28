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
import kotlinx.coroutines.test.runCurrent
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
    fun `an account without a phrase restores its keys from Import account's list of sources`() = runTest {
        // Imported by raw seed or from a backup file: the phrase screen alone has no way
        // forward for it. This opens Import account where raw seed and backup file are chosen.
        unavailable.value = setOf(account.substrateAddress)
        given(multiAccInteractor.unavailableWalletOptions(account.substrateAddress))
            .willReturn(readOnlyOptions(canShowPhraseAfterPin = false))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRestoreKeysAnotherWay()

        assertEquals(AccountDetailsViewModel.RestoreKeys.CHOOSE_SOURCE, viewModel.restoreKeysEvent.value)
        verify(mainRouter, never()).showPinForLogout(account.substrateAddress)
    }

    @Test
    fun `the list of sources is not opened for an account that signs`() = runTest {
        advanceUntilIdle()

        accountDetailsViewModel.onRestoreKeysAnotherWay()

        assertNull(accountDetailsViewModel.restoreKeysEvent.value)
    }

    @Test
    fun `a key that nothing has judged yet is shown as not checked`() = runTest {
        unavailable.value = setOf(account.substrateAddress)
        given(multiAccInteractor.unavailableWalletOptions(account.substrateAddress))
            .willReturn(readOnlyOptions(UnavailableWalletReason.NOT_CHECKED))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            UnavailableWalletReason.NOT_CHECKED,
            viewModel.accountDetailsScreenState.value?.readOnly?.reason,
        )
    }

    @Test
    fun `a Keystore failure while the screen opens is reported and does not end the app`() = runTest {
        // Account options is where every read-only notice sends the person.
        given(multiAccInteractor.getMnemonic(account))
            .willAnswer { throw IllegalStateException("KEYSTORE_BUSY") }

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            jp.co.soramitsu.common.R.string.failure_generic_title to
                jp.co.soramitsu.common.R.string.failure_generic_message,
            viewModel.errorFromResourceLiveData.value,
        )
        // The screen is there, without the Passphrase entry.
        assertEquals(account.substrateAddress, viewModel.accountDetailsScreenState.value?.address)
        assertEquals(false, viewModel.accountDetailsScreenState.value?.isMnemonicAvailable)
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
    fun `first name edit within initial debounce window is persisted`() = runTest {
        val fresh = viewModel()
        runCurrent()
        advanceTimeBy(100)
        fresh.onNameChange(TextFieldValue("Quickly pasted name"))
        advanceTimeBy(501)
        runCurrent()
        verify(multiAccInteractor).updateName(account.substrateAddress, "Quickly pasted name")
        verify(multiAccInteractor, never()).updateName(account.substrateAddress, "")
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

        accountDetailsViewModel.onWarningToggle()
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
