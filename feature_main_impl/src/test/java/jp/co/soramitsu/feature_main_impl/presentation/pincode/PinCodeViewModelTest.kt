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

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionPreview
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionResult
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionScope
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionTarget
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.domain.WalletRemovalBriefing
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.verifyNoInteractions
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.anyOrNull

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PinCodeViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    lateinit var interactor: PinCodeInteractor

    @Mock
    lateinit var mainInteractor: MainInteractor

    @Mock
    lateinit var mainRouter: MainRouter

    @Mock
    lateinit var selectNodeRouter: SelectNodeRouter

    @Mock
    lateinit var connectionManager: ConnectionManager

    @Mock
    lateinit var progress: WithProgress

    @Mock
    lateinit var resourceManager: ResourceManager

    @Mock
    lateinit var vibrator: DeviceVibrator

    private lateinit var pinCodeViewModel: PinCodeViewModel

    private val setupTitle = "Set pincode"
    private val enterPincode = "Enter pincode"
    private val confirmTitle = "Confirm title"
    private val connected = MutableStateFlow(true)

    @Before
    fun setUp() = runTest {
        given(resourceManager.getString(R.string.pincode_enter_pin_code)).willReturn(enterPincode)
        given(resourceManager.getString(R.string.pincode_set_your_pin_code)).willReturn(setupTitle)
        given(resourceManager.getString(R.string.pincode_confirm_your_pin_code)).willReturn(
            confirmTitle
        )
        given(connectionManager.connectionState).willReturn(connected)

        given(interactor.isBiometryEnabled()).willReturn(true)
        given(interactor.needsMigration()).willReturn(false)
        given(interactor.retrieveTriesUsed()).willReturn(0)
        given(interactor.retrieveTimerStartedTimestamp()).willReturn(0)

        pinCodeViewModel =
            PinCodeViewModel(
                interactor,
                mainInteractor,
                mainRouter,
                resourceManager,
                selectNodeRouter,
                progress,
                vibrator,
                connectionManager,
            )
    }

    @Test
    fun `start auth with CREATE_PIN_CODE action`() = runTest {
        val titleString = "Set pincode"
        given(resourceManager.getString(R.string.pincode_set_your_pin_code)).willReturn(titleString)

        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)

        advanceUntilIdle()

        assertEquals(PinCodeScreenState(toolbarTitleString = titleString), pinCodeViewModel.state)
    }

    @Test
    fun `start auth with OPEN_PASSPHRASE action`() = runTest {
        val titleString = "Set pincode"
        given(resourceManager.getString(R.string.pincode_enter_pin_code)).willReturn(titleString)

        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)

        advanceUntilIdle()

        assertEquals(
            PinCodeScreenState(
                toolbarTitleString = titleString,
                isBiometricButtonVisible = true
            ),
            pinCodeViewModel.state
        )
    }

    @Test
    fun `start auth with TIMEOUT_CHECK action and saved pin code in prefs`() = runTest {
        given(interactor.isCodeSet()).willReturn(true)
        val titleString = "Set pincode"
        given(resourceManager.getString(R.string.pincode_enter_pin_code)).willReturn(titleString)

        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)

        advanceUntilIdle()

        assertNotNull(pinCodeViewModel.showFingerPrintEventLiveData.value)
        assertEquals(PinCodeScreenState(toolbarTitleString = titleString), pinCodeViewModel.state)
        verify(interactor).isCodeSet()
        verifyNoInteractions(progress)
    }

    @Test
    fun `start auth with TIMEOUT_CHECK action and no saved pin code`() = runTest {
        given(interactor.isCodeSet()).willReturn(false)
        val titleString = "Set pincode"
        given(resourceManager.getString(R.string.pincode_set_your_pin_code)).willReturn(titleString)

        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)

        advanceUntilIdle()

        assertEquals(PinCodeScreenState(toolbarTitleString = titleString), pinCodeViewModel.state)
        verify(interactor).isCodeSet()
        verifyNoInteractions(progress)
    }

    @Test
    fun `pinCodeNumberClicked() changes progress and deleteBtn visibility`() = runTest {
        val titleString = "Set pincode"
        given(resourceManager.getString(R.string.pincode_set_your_pin_code)).willReturn(titleString)
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)

        advanceUntilIdle()

        pinCodeViewModel.pinCodeNumberClicked("1")

        assertEquals(
            PinCodeScreenState(
                toolbarTitleString = titleString,
                checkedDotsCount = 1,
                isBackButtonVisible = true
            ),
            pinCodeViewModel.state
        )
    }

    @Test
    fun `pinCodeDeleteClicked() changes progress and deleteBtn visibility`() = runTest {
        val titleString = "Set pincode"
        given(resourceManager.getString(R.string.pincode_set_your_pin_code)).willReturn(titleString)
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeDeleteClicked()
        pinCodeViewModel.pinCodeDeleteClicked()
        advanceUntilIdle()

        assertEquals(PinCodeScreenState(toolbarTitleString = titleString), pinCodeViewModel.state)
    }

    @Test
    fun `pin code overclicks`() = runTest {
        pinCodeViewModel.addresses = listOf("address")
        given(interactor.checkPin(anyString())).willReturn(true)

        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")
        pinCodeViewModel.pinCodeNumberClicked("6")

        delay(20L)

        verify(interactor).checkPin("123456")
        verify(mainRouter).popBackStack()
        verify(mainRouter).showBackupPassphrase("address")
        verifyNoInteractions(progress)
    }

    @Test
    fun `pin code entered once while creating`() = runTest {
        val titleString = "Set pincode"
        given(resourceManager.getString(R.string.pincode_confirm_your_pin_code)).willReturn(
            titleString
        )

        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")

        delay(20L)

        assertEquals(PinCodeScreenState(toolbarTitleString = titleString), pinCodeViewModel.state)
    }

    @Test
    fun `pin code entered correctly second time while creating`() = runTest {
        given(interactor.savePin(anyString())).willReturn(Unit)
        given(interactor.isBiometryAvailable()).willReturn(true)

        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")

        delay(20L)

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")

        delay(20L)

        pinCodeViewModel.checkInviteLiveData.observeForever {
            assertNotNull(it)
        }

        verify(interactor).savePin("123456")
        verifyNoInteractions(progress)
    }

    @Test
    fun `pin code entered wrong second time while creating`() = runTest {
        val titleString = "Set pincode"
        given(resourceManager.getString(R.string.pincode_set_your_pin_code)).willReturn(titleString)

        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("1")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("2")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("3")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("4")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("5")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("6")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("1")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("2")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("3")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("4")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("5")
        advanceUntilIdle()
        pinCodeViewModel.pinCodeNumberClicked("5")
        advanceUntilIdle()

        assertEquals(
            PinCodeScreenState(
                toolbarTitleString = titleString,
                enableShakeAnimation = true,
                isBackButtonVisible = false,
                checkedDotsCount = 0
            ),
            pinCodeViewModel.state
        )
    }

    @Test
    fun `pin code check error`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)

        given(interactor.checkPin(anyString())).willReturn(false)

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")

        delay(20L)

        assertTrue(pinCodeViewModel.state.enableShakeAnimation)

        verify(interactor).checkPin(anyString())
    }

    @Test
    fun `pin code entered correct with OPEN_PASSPHRASE action`() = runTest {
        pinCodeViewModel.addresses = listOf("address")
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)

        given(interactor.checkPin(anyString())).willReturn(true)

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")

        delay(20L)
        verify(interactor).checkPin(anyString())
        verify(mainRouter).showBackupPassphrase("address")
    }

    @Test
    fun `unusable wallet key keeps asking for the stored pin and never offers a new one`() =
        runTest {
            // The PIN is stored but cannot be decrypted right now (busy or missing Keystore key).
            given(interactor.isCodeSet())
                .willThrow(KeyMaterialUnavailableException("busy", transient = true))
            given(interactor.checkPin(anyString())).willReturn(true)

            pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)
            advanceUntilIdle()

            assertEquals(enterPincode, pinCodeViewModel.state.toolbarTitleString)
            repeat(6) { pinCodeViewModel.pinCodeNumberClicked("1") }
            advanceUntilIdle()

            // The typed PIN is checked against the stored one, not saved as a new PIN.
            verify(interactor).checkPin(anyString())
            verify(interactor, never()).savePin(anyString())
        }

    @Test
    fun `pin code entered correct with TIMEOUT_CHECK action`() = runTest {
        given(interactor.isCodeSet()).willReturn(true)

        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)

        given(interactor.checkPin(anyString())).willReturn(true)

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")

        delay(20L)

        pinCodeViewModel.checkInviteLiveData.observeForever {
            assertNotNull(it)
        }

        verify(interactor).isCodeSet()
        verify(interactor).checkPin(anyString())
        verify(mainRouter).popBackStack()
    }

    // Offline unlock: the PIN is checked on the phone, and an account whose claim check needs no
    // network (every account except an unchecked 15-word legacy phrase) enters the wallet while
    // SORA2 is disconnected, so Taira and offline addresses stay reachable. The legacy account
    // whose check needs the network waits for it (PinCodeAccountCheckTest).
    @Test
    fun `offline unlock grants access only after correct PIN when the account check needs no network`() = runTest {
        connected.value = false
        given(interactor.isCodeSet()).willReturn(true)
        given(interactor.checkPin(anyString())).willReturn(false, true)
        given(interactor.migrationCheckNeedsNetwork()).willReturn(false)
        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)
        advanceUntilIdle()

        "123456".forEach { pinCodeViewModel.pinCodeNumberClicked(it.toString()) }
        advanceUntilIdle()
        verify(mainRouter, never()).popBackStack()

        "123456".forEach { pinCodeViewModel.pinCodeNumberClicked(it.toString()) }
        advanceUntilIdle()
        verify(interactor, times(2)).checkPin("123456")
        verify(mainRouter).popBackStack()
        verify(interactor).needsMigration()
        assertEquals(false, pinCodeViewModel.state.migrating)
        assertEquals(false, pinCodeViewModel.state.checkNeedsNetwork)
    }

    @Test
    fun `offline onboarding enters wallet after PIN confirmation`() = runTest {
        connected.value = false
        given(interactor.isBiometryAvailable()).willReturn(false)
        given(interactor.migrationCheckNeedsNetwork()).willReturn(false)
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        advanceUntilIdle()
        "123456".forEach { pinCodeViewModel.pinCodeNumberClicked(it.toString()) }
        advanceUntilIdle()
        verify(mainRouter, never()).popBackStack()
        "123456".forEach { pinCodeViewModel.pinCodeNumberClicked(it.toString()) }
        advanceUntilIdle()
        verify(interactor).savePin("123456")
        verify(mainRouter).popBackStack()
        assertEquals(false, pinCodeViewModel.state.migrating)
    }

    @Test
    fun `pin code entered correct with CUSTOM_NODE action`() = runTest {
        pinCodeViewModel.addresses = listOf("address")
        pinCodeViewModel.startAuth(PinCodeAction.CUSTOM_NODE)

        given(interactor.checkPin(anyString())).willReturn(true)

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")

        advanceUntilIdle()
        verify(interactor).checkPin(anyString())
        verify(selectNodeRouter).returnFromPinCodeCheck()
    }

    @Test
    fun `pin code entered correct with SELECT_NODE action`() = runTest {
        pinCodeViewModel.addresses = listOf("address")
        pinCodeViewModel.startAuth(PinCodeAction.SELECT_NODE)

        given(interactor.checkPin(anyString())).willReturn(true)

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")

        advanceUntilIdle()
        verify(interactor).checkPin(anyString())
        verify(selectNodeRouter).returnFromPinCodeCheck()
    }

    @Test
    fun `back pressed closing the app on CREATE_PIN_CODE action`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        advanceUntilIdle()
        pinCodeViewModel.onBackPressed()

        pinCodeViewModel.closeAppLiveData.observeForever {
            assertNotNull(it)
        }
        assertNotNull(pinCodeViewModel.closeAppLiveData.value)
    }

    @Test
    fun `back pressed closing the app on TIMEOUT_CHECK action`() = runTest {
        given(interactor.isCodeSet()).willReturn(true)

        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)
        advanceUntilIdle()
        pinCodeViewModel.onBackPressed()

        pinCodeViewModel.closeAppLiveData.observeForever {
            assertNotNull(it)
        }
        assertNotNull(pinCodeViewModel.closeAppLiveData.value)
    }

    @Test
    fun `back pressed hiding pin code view on OPEN_PASSPHRASE action`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)
        advanceUntilIdle()
        pinCodeViewModel.onBackPressed()
    }

    @Test
    fun `back pressed leads to reset pin view on CREATE_PIN_CODE action`() = runTest {
        val titleString = "Set pincode"
        given(resourceManager.getString(R.string.pincode_set_your_pin_code)).willReturn(titleString)

        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        advanceUntilIdle()
        pinCodeViewModel.onBackPressed()
        advanceUntilIdle()

        assertEquals(
            PinCodeScreenState(toolbarTitleString = titleString, checkedDotsCount = 4),
            pinCodeViewModel.state
        )
    }

    @Test
    fun `onResume() starts fingerprint scanner on OPEN_PASSPHRASE action`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        advanceUntilIdle()
        pinCodeViewModel.onResume()

        pinCodeViewModel.startFingerprintScannerEventLiveData.observeForever {
            assertNotNull(it)
        }
    }

    @Test
    fun `onResume() starts fingerprint scanner on TIMEOUT_CHECK action`() = runTest {
        given(interactor.isCodeSet()).willReturn(true)

        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)
        advanceUntilIdle()
        pinCodeViewModel.onResume()

        pinCodeViewModel.startFingerprintScannerEventLiveData.observeForever {
            assertNotNull(it)
        }
    }

    @Test
    fun `fingerprint scanner success leads to passphrase screen on OPEN_PASSPHRASE action`() =
        runTest {
            pinCodeViewModel.addresses = listOf("address")

            pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)
            advanceUntilIdle()
            pinCodeViewModel.onAuthenticationSucceeded()
            advanceUntilIdle()
            verify(mainRouter).showBackupPassphrase("address")
        }

    @Test
    fun `fingerprint scanner success leads to check user fragment on TIMEOUT_CHECK action`() =
        runTest {
            given(interactor.isCodeSet()).willReturn(true)
            pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)
            advanceUntilIdle()
            pinCodeViewModel.onAuthenticationSucceeded()
            advanceUntilIdle()
            verify(mainRouter).popBackStack()
        }

    @Test
    fun `fingerprint scanner success with CUSTOM_NODE action EXPECT return to node details`() =
        runTest {
            pinCodeViewModel.startAuth(PinCodeAction.CUSTOM_NODE)
            advanceUntilIdle()
            pinCodeViewModel.onAuthenticationSucceeded()
            advanceUntilIdle()
            verify(selectNodeRouter).returnFromPinCodeCheck()
        }

    @Test
    fun `fingerprint scanner success with SELECT_NODE action EXPECT return to node details`() =
        runTest {
            pinCodeViewModel.startAuth(PinCodeAction.SELECT_NODE)
            advanceUntilIdle()
            pinCodeViewModel.onAuthenticationSucceeded()
            advanceUntilIdle()
            verify(selectNodeRouter).returnFromPinCodeCheck()
        }

//    @Test
//    fun `onAuthFailed() set fingerPrintAutFailedLiveData value`() = runTest {
//        pinCodeViewModel.onAuthenticationFailed()
//        advanceUntilIdle()
//        pinCodeViewModel.fingerPrintAutFailedLiveData.observeForever {
//            assertNotNull(it)
//        }
//    }
//
//    @Test
//    fun `onAuthenticationError() set fingerPrintErrorLiveData value`() {
//        val message = "test message"
//        pinCodeViewModel.onAuthenticationError(message)
//
//        pinCodeViewModel.fingerPrintAutFailedLiveData.observeForever {
//            assertEquals(message, it)
//        }
//    }

    // ---- "Forget account": the backup is shown before anything is removed ------------------

    private val phrase = listOf(
        "alpha", "bravo", "charlie", "delta", "echo", "foxtrot",
        "golf", "hotel", "india", "juliet", "kilo", "lima",
    )

    private fun briefing(
        removesEverything: Boolean = false,
        phraseWords: List<String> = phrase,
        seedHex: String = "",
        canExportJson: Boolean = true,
        retainedKeySets: Int = 0,
        restorableKeySets: Int = 0,
    ) = WalletRemovalBriefing(
        accounts = listOf(
            WalletRemovalBriefing.Account(
                address = "address",
                name = "Wallet",
                phraseWords = phraseWords,
                seedHex = seedHex,
                canExportJson = canExportJson,
            ),
        ),
        removesEverything = removesEverything,
        retainedKeySets = retainedKeySets,
        restorableKeySets = restorableKeySets,
    )

    private suspend fun TestScope.openRemovalScreen(briefing: WalletRemovalBriefing) {
        given(interactor.walletRemovalBriefing(listOf("address"))).willReturn(briefing)
        pinCodeViewModel.addresses = listOf("address")
        pinCodeViewModel.startAuth(PinCodeAction.LOGOUT)
        advanceUntilIdle()
        pinCodeViewModel.onAuthenticationSucceeded()
        advanceUntilIdle()
    }

    private fun acknowledgeWrittenDown() {
        pinCodeViewModel.onRemovalRevealed()
        pinCodeViewModel.onRemovalWrittenDown(true)
    }

    @Test
    fun `after the PIN the matching recovery phrase is shown and nothing is removed yet`() = runTest {
        openRemovalScreen(briefing())

        val removal = requireNotNull(pinCodeViewModel.removal)
        assertEquals(phrase, removal.accounts.single().phraseWords)
        assertTrue(removal.showsBackup)
        assertFalse(removal.revealed)
        assertFalse(removal.canRemove)
        verify(interactor, never()).createWalletDeletionPreview(anyOrNull())
    }

    @Test
    fun `nothing is removed until the phrase was shown and written down`() = runTest {
        openRemovalScreen(briefing())

        // Ticking before the phrase was shown does not count.
        pinCodeViewModel.onRemovalWrittenDown(true)
        assertFalse(requireNotNull(pinCodeViewModel.removal).writtenDown)
        pinCodeViewModel.onRemovalConfirmed()
        advanceUntilIdle()
        verify(interactor, never()).createWalletDeletionPreview(anyOrNull())

        pinCodeViewModel.onRemovalRevealed()
        assertFalse(requireNotNull(pinCodeViewModel.removal).canRemove)
        pinCodeViewModel.onRemovalWrittenDown(true)
        assertTrue(requireNotNull(pinCodeViewModel.removal).canRemove)
    }

    @Test
    fun `removing the only account removes everything and restarts the app`() = runTest {
        val preview = deletionPreview(WalletDeletionScope.ALL)
        given(interactor.createWalletDeletionPreview(listOf("address"))).willReturn(preview)
        given(interactor.confirmAndExecuteWalletDeletion(preview)).willReturn(
            WalletDeletionResult(WalletDeletionScope.ALL, "")
        )
        openRemovalScreen(briefing(removesEverything = true, retainedKeySets = 2))
        assertTrue(requireNotNull(pinCodeViewModel.removal).removesEverything)
        assertEquals(2, requireNotNull(pinCodeViewModel.removal).retainedKeySets)

        acknowledgeWrittenDown()
        pinCodeViewModel.onRemovalConfirmed()
        advanceUntilIdle()

        verify(interactor).confirmAndExecuteWalletDeletion(preview)
        assertEquals(Unit, pinCodeViewModel.resetApplicationEvent.value)
        assertNull(pinCodeViewModel.switchAccountEvent.value)
        verify(progress).showProgress()
        verify(progress).hideProgress()
    }

    @Test
    fun `removing one of several accounts switches to another account`() = runTest {
        val preview = deletionPreview(WalletDeletionScope.SINGLE)
        given(interactor.createWalletDeletionPreview(listOf("address"))).willReturn(preview)
        given(interactor.confirmAndExecuteWalletDeletion(preview)).willReturn(
            WalletDeletionResult(WalletDeletionScope.SINGLE, "address2")
        )
        openRemovalScreen(briefing())

        acknowledgeWrittenDown()
        pinCodeViewModel.onRemovalConfirmed()
        advanceUntilIdle()

        verify(interactor).confirmAndExecuteWalletDeletion(preview)
        assertEquals(Unit, pinCodeViewModel.switchAccountEvent.value)
        assertNull(pinCodeViewModel.resetApplicationEvent.value)
    }

    @Test
    fun `a raw seed is shown when no recovery phrase matches the account`() = runTest {
        openRemovalScreen(briefing(phraseWords = emptyList(), seedHex = "ab".repeat(32)))

        val account = requireNotNull(pinCodeViewModel.removal).accounts.single()
        assertTrue(account.phraseWords.isEmpty())
        assertEquals("0x" + "ab".repeat(32), account.seed)
        assertTrue(requireNotNull(pinCodeViewModel.removal).showsBackup)
    }

    @Test
    fun `an account without a matching backup says so, offers the JSON export and asks for its own acknowledgement`() =
        runTest {
            openRemovalScreen(briefing(phraseWords = emptyList(), canExportJson = true))

            val removal = requireNotNull(pinCodeViewModel.removal)
            assertFalse(removal.showsBackup)
            assertTrue(removal.hasAccountWithoutBackup)
            assertFalse(removal.canRemove)

            pinCodeViewModel.onRemovalExportJson()
            verify(mainRouter).showBackupJson(listOf("address"))

            // "I have written it down" is not what is asked for here.
            pinCodeViewModel.onRemovalWrittenDown(true)
            assertFalse(requireNotNull(pinCodeViewModel.removal).canRemove)
            pinCodeViewModel.onRemovalUnderstoodNoBackup(true)
            assertTrue(requireNotNull(pinCodeViewModel.removal).canRemove)
        }

    @Test
    fun `a read-only account without a backup is not offered an export that cannot be made`() = runTest {
        openRemovalScreen(briefing(phraseWords = emptyList(), canExportJson = false))

        pinCodeViewModel.onRemovalExportJson()

        verify(mainRouter, never()).showBackupJson(anyOrNull())
        assertFalse(requireNotNull(pinCodeViewModel.removal).accounts.single().canExportJson)
    }

    @Test
    fun `cancelling removes nothing and leaves the screen`() = runTest {
        openRemovalScreen(briefing())

        pinCodeViewModel.onBackPressed()

        assertNull(pinCodeViewModel.removal)
        verify(mainRouter).popBackStack()
        verify(interactor, never()).createWalletDeletionPreview(anyOrNull())
    }

    @Test
    fun `the PIN is asked for once - returning to the removal screen starts no new authentication`() =
        runTest {
            openRemovalScreen(briefing())
            pinCodeViewModel.onRemovalRevealed()
            val before = pinCodeViewModel.startFingerprintScannerEventLiveData.value

            pinCodeViewModel.onResume()
            advanceUntilIdle()

            assertEquals(before, pinCodeViewModel.startFingerprintScannerEventLiveData.value)
            // The screen is as the person left it, and the briefing was read once.
            assertTrue(requireNotNull(pinCodeViewModel.removal).revealed)
            verify(interactor, times(1)).walletRemovalBriefing(listOf("address"))
        }

    @Test
    fun `found wallets the PIN can still restore need their own acknowledgement`() = runTest {
        // Removing the only account removes the PIN: the two found wallets it can restore
        // today would need their own recovery phrases afterwards.
        openRemovalScreen(briefing(removesEverything = true, retainedKeySets = 3, restorableKeySets = 2))
        val removal = requireNotNull(pinCodeViewModel.removal)
        assertEquals(2, removal.restorableKeySets)
        assertTrue(removal.strandsFoundWallets)

        acknowledgeWrittenDown()
        assertFalse(requireNotNull(pinCodeViewModel.removal).canRemove)
        pinCodeViewModel.onRemovalConfirmed()
        advanceUntilIdle()
        verify(interactor, never()).createWalletDeletionPreview(anyOrNull())

        pinCodeViewModel.onRemovalUnderstoodFoundWallets(true)
        assertTrue(requireNotNull(pinCodeViewModel.removal).canRemove)
    }

    @Test
    fun `one of several accounts never asks about found wallets`() = runTest {
        // The PIN stays, so "Wallets found on this phone" keeps working.
        openRemovalScreen(briefing(removesEverything = false, restorableKeySets = 2))
        assertFalse(requireNotNull(pinCodeViewModel.removal).strandsFoundWallets)

        acknowledgeWrittenDown()
        assertTrue(requireNotNull(pinCodeViewModel.removal).canRemove)
    }

    @Test
    fun `leaving the removal screen veils the phrase and keeps what was acknowledged`() = runTest {
        openRemovalScreen(briefing())
        acknowledgeWrittenDown()
        assertTrue(requireNotNull(pinCodeViewModel.removal).revealed)

        // Another app, the recents screen or the JSON export came in front.
        pinCodeViewModel.onRemovalHidden()

        val hidden = requireNotNull(pinCodeViewModel.removal)
        assertFalse(hidden.revealed)
        // It was shown and ticked: that stays, and Remove needs no second reveal.
        assertTrue(hidden.writtenDown)
        assertTrue(hidden.canRemove)
    }

    @Test
    fun `coming back from the JSON export keeps the removal screen and asks for nothing`() = runTest {
        openRemovalScreen(briefing(phraseWords = emptyList()))
        pinCodeViewModel.onRemovalUnderstoodNoBackup(true)
        pinCodeViewModel.onRemovalExportJson()
        verify(mainRouter).showBackupJson(listOf("address"))

        // Back on the export returns to this destination, whose view is created again.
        pinCodeViewModel.startAuth(PinCodeAction.LOGOUT)
        pinCodeViewModel.onResume()
        advanceUntilIdle()

        val removal = requireNotNull(pinCodeViewModel.removal)
        assertTrue(removal.understoodNoBackup)
        assertTrue(removal.canRemove)
        verify(interactor, times(1)).walletRemovalBriefing(listOf("address"))
    }

    @Test
    fun `an unfinished transaction is said right after the PIN, before any phrase is shown`() = runTest {
        given(interactor.walletRemovalBriefing(listOf("address")))
            .willThrow(IllegalStateException("WALLET_DELETION_PENDING_TRANSACTIONS"))
        given(resourceManager.getString(R.string.account_removal_not_removed_title))
            .willReturn("Not removed")
        given(resourceManager.getString(R.string.account_removal_pending_transactions))
            .willReturn("Wait for the transaction")
        pinCodeViewModel.addresses = listOf("address")
        pinCodeViewModel.startAuth(PinCodeAction.LOGOUT)
        advanceUntilIdle()

        pinCodeViewModel.onAuthenticationSucceeded()
        advanceUntilIdle()

        // No removal screen, so no phrase to reveal and write down for a removal that
        // cannot happen yet; the text says what has to happen first.
        assertNull(pinCodeViewModel.removal)
        assertEquals(
            "Not removed" to "Wait for the transaction",
            pinCodeViewModel.alertDialogLiveData.value,
        )
        assertNull(pinCodeViewModel.errorFromResourceLiveData.value)
        verify(mainRouter, never()).popBackStack()
    }

    @Test
    fun `read-only mode of the whole app is said right after the PIN`() = runTest {
        given(interactor.walletRemovalBriefing(listOf("address")))
            .willThrow(IllegalStateException("WALLET_RECOVERY_READ_ONLY"))
        pinCodeViewModel.addresses = listOf("address")
        pinCodeViewModel.startAuth(PinCodeAction.LOGOUT)
        advanceUntilIdle()

        pinCodeViewModel.onAuthenticationSucceeded()
        advanceUntilIdle()

        assertNull(pinCodeViewModel.removal)
        assertEquals(
            R.string.safe_mode_read_only_title to R.string.safe_mode_read_only_body,
            pinCodeViewModel.errorFromResourceLiveData.value,
        )
    }

    @Test
    fun `a transaction that started while the screen was open is named at the last step too`() = runTest {
        given(interactor.createWalletDeletionPreview(listOf("address")))
            .willThrow(IllegalStateException("WALLET_DELETION_PENDING_TRANSACTIONS"))
        given(interactor.walletDeletionFailureCode()).willReturn(null)
        given(interactor.areListed(listOf("address"))).willReturn(true)
        given(resourceManager.getString(R.string.account_removal_not_removed_title))
            .willReturn("Not removed")
        given(resourceManager.getString(R.string.account_removal_pending_transactions))
            .willReturn("Wait for the transaction")
        openRemovalScreen(briefing())
        acknowledgeWrittenDown()

        pinCodeViewModel.onRemovalConfirmed()
        advanceUntilIdle()

        assertEquals(
            "Not removed" to "Wait for the transaction",
            pinCodeViewModel.alertDialogLiveData.value,
        )
        assertFalse(requireNotNull(pinCodeViewModel.removal).working)
        verify(interactor, never()).confirmAndExecuteWalletDeletion(anyOrNull())
    }

    @Test
    fun `a check that does not pass keeps the screen and says that nothing was removed`() = runTest {
        given(interactor.createWalletDeletionPreview(listOf("address")))
            .willThrow(IllegalStateException("WALLET_DELETION_SELECTION_INVALID"))
        given(interactor.walletDeletionFailureCode()).willReturn(null)
        given(interactor.areListed(listOf("address"))).willReturn(true)
        given(resourceManager.getString(R.string.account_removal_not_removed_title))
            .willReturn("Not removed")
        given(resourceManager.getString(R.string.account_removal_not_removed))
            .willReturn("Nothing was removed (%1\$s)")
        openRemovalScreen(briefing())
        acknowledgeWrittenDown()

        pinCodeViewModel.onRemovalConfirmed()
        advanceUntilIdle()

        assertEquals(
            "Not removed" to "Nothing was removed (WALLET_DELETION_SELECTION_INVALID)",
            pinCodeViewModel.alertDialogLiveData.value,
        )
        val removal = requireNotNull(pinCodeViewModel.removal)
        assertFalse(removal.working)
        assertTrue(removal.canRemove)
        assertNull(pinCodeViewModel.resetApplicationEvent.value)
        verify(interactor, never()).confirmAndExecuteWalletDeletion(anyOrNull())
        verify(progress).hideProgress()
    }

    @Test
    fun `journaled deletion failure hides progress and restarts into recovery`() = runTest {
        val preview = deletionPreview(WalletDeletionScope.SINGLE)
        given(interactor.createWalletDeletionPreview(listOf("address"))).willReturn(preview)
        given(interactor.confirmAndExecuteWalletDeletion(preview))
            .willThrow(IllegalStateException("WALLET_DELETION_PREFERENCES_CHANGED"))
        given(interactor.walletDeletionFailureCode())
            .willReturn("WALLET_DELETION_PREFERENCES_CHANGED")
        openRemovalScreen(briefing())
        acknowledgeWrittenDown()

        pinCodeViewModel.onRemovalConfirmed()
        advanceUntilIdle()

        verify(progress).showProgress()
        verify(progress).hideProgress()
        assertEquals(
            "WALLET_DELETION_PREFERENCES_CHANGED",
            pinCodeViewModel.errorLiveData.value,
        )
        assertEquals(Unit, pinCodeViewModel.resetApplicationEvent.value)
    }

    @Test
    fun `a removal that failed after the accounts were removed restarts instead of claiming nothing happened`() =
        runTest {
            val preview = deletionPreview(WalletDeletionScope.SINGLE)
            given(interactor.createWalletDeletionPreview(listOf("address"))).willReturn(preview)
            given(interactor.confirmAndExecuteWalletDeletion(preview))
                .willThrow(IllegalStateException("disk I/O error"))
            given(interactor.walletDeletionFailureCode()).willReturn(null)
            given(interactor.areListed(listOf("address"))).willReturn(false)
            openRemovalScreen(briefing())
            acknowledgeWrittenDown()

            pinCodeViewModel.onRemovalConfirmed()
            advanceUntilIdle()

            assertEquals(Unit, pinCodeViewModel.resetApplicationEvent.value)
            assertNull(pinCodeViewModel.alertDialogLiveData.value)
            assertNull(pinCodeViewModel.removal)
        }

    @Test
    fun `accounts that changed while the screen was open are described again and nothing is removed`() =
        runTest {
            // The screen described the removal of one of several accounts; by now it is the last.
            val preview = deletionPreview(WalletDeletionScope.ALL)
            given(interactor.createWalletDeletionPreview(listOf("address"))).willReturn(preview)
            openRemovalScreen(briefing(removesEverything = false))
            acknowledgeWrittenDown()

            pinCodeViewModel.onRemovalConfirmed()
            advanceUntilIdle()

            verify(interactor, never()).confirmAndExecuteWalletDeletion(anyOrNull())
            verify(interactor, times(2)).walletRemovalBriefing(listOf("address"))
            val removal = requireNotNull(pinCodeViewModel.removal)
            assertFalse(removal.revealed)
            assertFalse(removal.canRemove)
        }

    @Test
    fun `when the backup cannot be read nothing is removed and the message stays on the screen`() = runTest {
        given(interactor.walletRemovalBriefing(listOf("address")))
            .willThrow(IllegalStateException("KEYSTORE_BUSY"))
        pinCodeViewModel.addresses = listOf("address")
        pinCodeViewModel.startAuth(PinCodeAction.LOGOUT)
        advanceUntilIdle()

        pinCodeViewModel.onAuthenticationSucceeded()
        advanceUntilIdle()

        assertNull(pinCodeViewModel.removal)
        assertEquals(
            R.string.failure_generic_title to R.string.failure_generic_message,
            pinCodeViewModel.errorFromResourceLiveData.value,
        )
        // Leaving the screen at once would take the message with it.
        verify(mainRouter, never()).popBackStack()
        verify(interactor, never()).createWalletDeletionPreview(anyOrNull())
    }

    private fun deletionPreview(scope: WalletDeletionScope): WalletDeletionPreview =
        WalletDeletionPreview(
            previewId = "preview",
            scope = scope,
            targets = listOf(WalletDeletionTarget("address", "Wallet")),
            selectedBefore = "address",
            selectedAfter = if (scope == WalletDeletionScope.ALL) "" else "address2",
            snapshotHash = "0".repeat(64),
            beforePreferencesHash = "1".repeat(64),
            afterPreferencesHash = "2".repeat(64),
            removeLegacyUnsuffixed = false,
            expiresAtElapsedRealtime = Long.MAX_VALUE,
        )
}
