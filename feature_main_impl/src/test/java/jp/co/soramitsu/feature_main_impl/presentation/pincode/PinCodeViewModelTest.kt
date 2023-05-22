/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

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
    lateinit var progress: WithProgress

    @Mock
    lateinit var resourceManager: ResourceManager

    @Mock
    lateinit var vibrator: DeviceVibrator

    private lateinit var pinCodeViewModel: PinCodeViewModel

    private val setupTitle = "Set pincode"
    private val enterPincode = "Enter pincode"
    private val confirmTitle = "Confirm title"

    @Before
    fun setUp() = runTest {
        given(resourceManager.getString(R.string.pincode_enter_pin_code)).willReturn(enterPincode)
        given(resourceManager.getString(R.string.pincode_set_your_pin_code)).willReturn(setupTitle)
        given(resourceManager.getString(R.string.pincode_confirm_your_pin_code)).willReturn(confirmTitle)

        given(interactor.isBiometryEnabled()).willReturn(true)
        given(interactor.needsMigration()).willReturn(false)
        given(interactor.retrieveTriesUsed()).willReturn(0)
        given(interactor.retrieveTimerStartedTimestamp()).willReturn(0)

        pinCodeViewModel =
            PinCodeViewModel(interactor, mainInteractor, mainRouter, resourceManager, selectNodeRouter, progress, vibrator)
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

        assertEquals(PinCodeScreenState(toolbarTitleString = titleString, isBiometricButtonVisible = true), pinCodeViewModel.state)
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

        assertEquals(PinCodeScreenState(toolbarTitleString = titleString, checkedDotsCount = 1, isBackButtonVisible = true), pinCodeViewModel.state)
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
        given(resourceManager.getString(R.string.pincode_confirm_your_pin_code)).willReturn(titleString)

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

        assertEquals(PinCodeScreenState(toolbarTitleString = titleString, enableShakeAnimation = true, isBackButtonVisible = false, checkedDotsCount = 0), pinCodeViewModel.state)
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

        assertEquals(PinCodeScreenState(toolbarTitleString = titleString, checkedDotsCount = 4), pinCodeViewModel.state)
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
    fun `fingerprint scanner success leads to passphrase screen on OPEN_PASSPHRASE action`() = runTest {
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

    @Test
    fun `logout ok pressed with only 1 account EXPECT full logout`() = runTest {
        given(mainInteractor.getSoraAccountsCount()).willReturn(1)
        pinCodeViewModel.logoutOkPressed()
        advanceUntilIdle()

        verify(interactor).fullLogout()
        assertNotNull(pinCodeViewModel.resetApplicationEvent)
    }

    @Test
    fun `logout ok pressed with multiple accounts EXPECT clear account data`() = runTest {
        given(mainInteractor.getSoraAccountsCount()).willReturn(2)
        given(mainInteractor.getCurUserAddress()).willReturn("address")
        given(mainInteractor.getSoraAccountsList()).willReturn(
            listOf(
                SoraAccount(
                    substrateAddress = "address2",
                    accountName = ""
                )
            )
        )
        pinCodeViewModel.addresses = listOf("address")

        pinCodeViewModel.logoutOkPressed()
        advanceUntilIdle()

        verify(interactor).clearAccountData("address")
    }

    @Test
    fun `logout ok pressed with multiple accounts EXPECT switch account`() = runTest {
        val account = SoraAccount(
            substrateAddress = "address2",
            accountName = ""
        )
        given(mainInteractor.getSoraAccountsCount()).willReturn(2)
        given(mainInteractor.getCurUserAddress()).willReturn("address")
        given(mainInteractor.getSoraAccountsList()).willReturn(
            listOf(
                account
            )
        )
        pinCodeViewModel.addresses = listOf("address")

        pinCodeViewModel.logoutOkPressed()
        advanceUntilIdle()
        verify(mainInteractor).setCurSoraAccount(account)
        assertNotNull(pinCodeViewModel.switchAccountEvent)
    }

    @Test
    fun `logout EXPECT check accounts amount`() = runTest {
        given(mainInteractor.getSoraAccountsCount()).willReturn(2)

        pinCodeViewModel.startAuth(PinCodeAction.LOGOUT)
        advanceUntilIdle()
        pinCodeViewModel.onAuthenticationSucceeded()
        advanceUntilIdle()

        verify(mainInteractor).getSoraAccountsCount()
    }

    @Test
    fun `logout with multiple accounts EXPECT logout message`() = runTest {
        given(mainInteractor.getSoraAccountsCount()).willReturn(2)
        given(resourceManager.getString(R.string.logout_dialog_body)).willReturn("Logout")

        pinCodeViewModel.startAuth(PinCodeAction.LOGOUT)
        advanceUntilIdle()
        pinCodeViewModel.onAuthenticationSucceeded()
        advanceUntilIdle()

        assertEquals("Logout", pinCodeViewModel.logoutEvent.value)
    }

    @Test
    fun `logout with single account EXPECT logout message`() = runTest {
        given(mainInteractor.getSoraAccountsCount()).willReturn(1)
        given(resourceManager.getString(R.string.logout_dialog_body)).willReturn("Logout")
        given(resourceManager.getString(R.string.logout_remove_nodes_body)).willReturn(" Remove nodes")

        pinCodeViewModel.startAuth(PinCodeAction.LOGOUT)
        advanceUntilIdle()
        pinCodeViewModel.onAuthenticationSucceeded()
        advanceUntilIdle()

        assertEquals("Logout Remove nodes", pinCodeViewModel.logoutEvent.value)
    }
}
