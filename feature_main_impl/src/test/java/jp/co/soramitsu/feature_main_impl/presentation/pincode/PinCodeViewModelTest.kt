/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    lateinit var progress: WithProgress

    @Mock
    lateinit var vibrator: DeviceVibrator

    private lateinit var pinCodeViewModel: PinCodeViewModel

    @Before
    fun setUp() = runTest {
        given(interactor.isBiometryEnabled()).willReturn(true)
        given(interactor.needsMigration()).willReturn(false)

        pinCodeViewModel =
            PinCodeViewModel(interactor, mainInteractor, mainRouter, progress, vibrator)
    }

    @Test
    fun `start auth with CREATE_PIN_CODE action`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)

        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
            assertEquals(R.string.pincode_set_your_pin_code, it)
        }
        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
            assertFalse(it)
        }
        advanceUntilIdle()
        assertEquals(
            R.string.pincode_set_your_pin_code,
            pinCodeViewModel.toolbarTitleResLiveData.value
        )
        assertFalse(pinCodeViewModel.backButtonVisibilityLiveData.value!!)
    }

    @Test
    fun `start auth with OPEN_PASSPHRASE action`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)

        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
            assertEquals(R.string.pincode_enter_pin_code, it)
        }
        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
            assertTrue(it)
        }
        advanceUntilIdle()
        assertNotNull(pinCodeViewModel.showFingerPrintEventLiveData.value)
        assertTrue(pinCodeViewModel.backButtonVisibilityLiveData.value!!)
        assertEquals(
            R.string.pincode_enter_pin_code,
            pinCodeViewModel.toolbarTitleResLiveData.value
        )
    }

    @Test
    fun `start auth with TIMEOUT_CHECK action and saved pin code in prefs`() = runTest {
        given(interactor.isCodeSet()).willReturn(true)

        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)

        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
            assertEquals(R.string.pincode_enter_pin_code, it)
        }
        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
            assertFalse(it)
        }
        advanceUntilIdle()
        assertNotNull(pinCodeViewModel.showFingerPrintEventLiveData.value)
        assertEquals(
            R.string.pincode_enter_pin_code,
            pinCodeViewModel.toolbarTitleResLiveData.value
        )
        assertFalse(pinCodeViewModel.backButtonVisibilityLiveData.value!!)

        verify(interactor).isCodeSet()
        verifyNoInteractions(progress)
    }

    @Test
    fun `start auth with TIMEOUT_CHECK action and no saved pin code`() = runTest {
        given(interactor.isCodeSet()).willReturn(false)

        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)

        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
            assertEquals(R.string.pincode_set_your_pin_code, it)
        }
        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
            assertFalse(it)
        }
        advanceUntilIdle()
        assertEquals(
            R.string.pincode_set_your_pin_code,
            pinCodeViewModel.toolbarTitleResLiveData.value
        )
        assertFalse(pinCodeViewModel.backButtonVisibilityLiveData.value!!)

        verify(interactor).isCodeSet()
        verifyNoInteractions(progress)
    }

    @Test
    fun `pinCodeNumberClicked() changes progress and deleteBtn visibility`() {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        pinCodeViewModel.pinCodeNumberClicked("1")

        pinCodeViewModel.pinCodeProgressLiveData.observeForever {
            assertEquals(1, it)
        }
        pinCodeViewModel.deleteButtonVisibilityLiveData.observeForever {
            assertTrue(it)
        }

        assertEquals(1, pinCodeViewModel.pinCodeProgressLiveData.value)
        assertTrue(pinCodeViewModel.deleteButtonVisibilityLiveData.value!!)
    }

    @Test
    fun `pinCodeDeleteClicked() changes progress and deleteBtn visibility`() {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeDeleteClicked()
        pinCodeViewModel.pinCodeDeleteClicked()

        pinCodeViewModel.pinCodeProgressLiveData.observeForever {}
        pinCodeViewModel.deleteButtonVisibilityLiveData.observeForever {}

        assertEquals(0, pinCodeViewModel.pinCodeProgressLiveData.value)
        assertFalse(pinCodeViewModel.deleteButtonVisibilityLiveData.value!!)
    }

    @Test
    fun `pin code overclicks`() = runTest {
        given(interactor.checkPin(anyString())).willReturn(true)

        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")
        pinCodeViewModel.pinCodeNumberClicked("6")

        pinCodeViewModel.backButtonVisibilityLiveData.observeForever { }
        pinCodeViewModel.pinCodeProgressLiveData.observeForever { }
        pinCodeViewModel.toolbarTitleResLiveData.observeForever { }

        delay(20L)

        verify(interactor).checkPin("123456")
        verify(mainRouter).popBackStack()
        verify(mainRouter).showPassphrase()
        verifyNoInteractions(progress)
    }

    @Test
    fun `pin code entered once while creating`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("5")
        pinCodeViewModel.pinCodeNumberClicked("6")

        delay(20L)

        pinCodeViewModel.backButtonVisibilityLiveData.observeForever { }
        pinCodeViewModel.pinCodeProgressLiveData.observeForever { }
        pinCodeViewModel.toolbarTitleResLiveData.observeForever { }

        assertEquals(0, pinCodeViewModel.pinCodeProgressLiveData.value!!)
        assertEquals(
            R.string.pincode_confirm_your_pin_code,
            pinCodeViewModel.toolbarTitleResLiveData.value
        )
        assertTrue(pinCodeViewModel.backButtonVisibilityLiveData.value!!)
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

        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
            assertEquals(R.string.pincode_set_your_pin_code, it)
        }

        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
            assertFalse(it)
        }

        pinCodeViewModel.errorFromResourceLiveData.observeForever {
            assertEquals(R.string.pincode_repeat_error, it.peekContent())
        }
        advanceUntilIdle()
        assertEquals(
            R.string.pincode_set_your_pin_code,
            pinCodeViewModel.toolbarTitleResLiveData.value
        )
        assertFalse(pinCodeViewModel.backButtonVisibilityLiveData.value!!)
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

        pinCodeViewModel.wrongPinCodeEventLiveData.observeForever {
            assertNotNull(it)
        }

        verify(interactor).checkPin(anyString())
    }

    @Test
    fun `pin code entered correct with OPEN_PASSPHRASE action`() = runTest {
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
        verify(mainRouter).showPassphrase()
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
    fun `back pressed closing the app on CREATE_PIN_CODE action`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        advanceUntilIdle()
        pinCodeViewModel.backPressed()

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
        pinCodeViewModel.backPressed()

        pinCodeViewModel.closeAppLiveData.observeForever {
            assertNotNull(it)
        }
        assertNotNull(pinCodeViewModel.closeAppLiveData.value)
    }

    @Test
    fun `back pressed hiding pin code view on OPEN_PASSPHRASE action`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)
        advanceUntilIdle()
        pinCodeViewModel.backPressed()
    }

    @Test
    fun `back pressed leads to reset pin view on CREATE_PIN_CODE action`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        advanceUntilIdle()
        pinCodeViewModel.backPressed()

        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
            assertFalse(it)
        }

        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
            assertEquals(R.string.pincode_set_your_pin_code, it)
        }

        assertFalse(pinCodeViewModel.backButtonVisibilityLiveData.value!!)
        assertEquals(
            R.string.pincode_set_your_pin_code,
            pinCodeViewModel.toolbarTitleResLiveData.value
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
    fun `fingerprint scanner success leads to passphrase screen on OPEN_PASSPHRASE action`() = runTest {
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)
        advanceUntilIdle()
        pinCodeViewModel.onAuthenticationSucceeded()

        verify(mainRouter).showPassphrase()
    }

    @Test
    fun `fingerprint scanner success leads to check user fragment on TIMEOUT_CHECK action`() =
        runTest {
            given(interactor.isCodeSet()).willReturn(true)
            pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)
            advanceUntilIdle()
            pinCodeViewModel.onAuthenticationSucceeded()
            verify(mainRouter).popBackStack()
        }

    @Test
    fun `onAuthFailed() set fingerPrintAutFailedLiveData value`() {
        pinCodeViewModel.onAuthenticationFailed()

        pinCodeViewModel.fingerPrintAutFailedLiveData.observeForever {
            assertNotNull(it)
        }
    }

    @Test
    fun `onAuthenticationError() set fingerPrintErrorLiveData value`() {
        val message = "test message"
        pinCodeViewModel.onAuthenticationError(message)

        pinCodeViewModel.fingerPrintAutFailedLiveData.observeForever {
            assertEquals(message, it)
        }
    }

    @Test
    fun `logout ok pressed with only 1 account EXPECT full logout`() = runTest {
        given(mainInteractor.getSoraAccountsCount()).willReturn(1)
        pinCodeViewModel.logoutOkPressed()
        advanceUntilIdle()

        verify(interactor).resetUser()
        assertNotNull(pinCodeViewModel.resetApplicationEvent)
    }

    @Test
    fun `logout ok pressed with multiple accounts EXPECT clear account data`() = runTest {
        given(mainInteractor.getSoraAccountsCount()).willReturn(2)
        given(mainInteractor.getCurUserAddress()).willReturn("address")
        given(mainInteractor.soraAccountsList()).willReturn(
            listOf(
                SoraAccount(
                    substrateAddress = "address2",
                    accountName = ""
                )
            )
        )

        pinCodeViewModel.logoutOkPressed()
        advanceUntilIdle()

        verify(interactor).clearAccountData("address")
    }

    @Test
    fun `logout ok pressed with multiple accounts EXPECT switch account`() = runTest {
        given(mainInteractor.getSoraAccountsCount()).willReturn(2)
        given(mainInteractor.getCurUserAddress()).willReturn("address")
        given(mainInteractor.soraAccountsList()).willReturn(
            listOf(
                SoraAccount(
                    substrateAddress = "address2",
                    accountName = ""
                )
            )
        )

        pinCodeViewModel.logoutOkPressed()
        advanceUntilIdle()
        verify(mainInteractor).setCurSoraAccount("address2")
        assertNotNull(pinCodeViewModel.switchAccountEvent)
    }
}