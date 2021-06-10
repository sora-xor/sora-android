/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
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
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PinCodeViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()
    @Rule
    @JvmField
    val schedulersRule = RxSchedulersRule()

    @Mock
    lateinit var interactor: PinCodeInteractor
    @Mock
    lateinit var mainRouter: MainRouter
    @Mock
    lateinit var progress: WithProgress
    @Mock
    lateinit var vibrator: DeviceVibrator

    private val maxProgress = 4

    private lateinit var pinCodeViewModel: PinCodeViewModel

    @Before
    fun setUp() {
        given(interactor.isBiometryEnabled()).willReturn(Single.just(true))
        given(interactor.needsMigration()).willReturn(Single.just(false))

        pinCodeViewModel = PinCodeViewModel(interactor, mainRouter, progress, vibrator, maxProgress)
    }

    @Test
    fun `start auth with CREATE_PIN_CODE action`() {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)

        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
            assertEquals(R.string.pincode_set_your_pin_code, it)
        }
        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
            assertFalse(it)
        }

        assertEquals(
            R.string.pincode_set_your_pin_code,
            pinCodeViewModel.toolbarTitleResLiveData.value
        )
        assertFalse(pinCodeViewModel.backButtonVisibilityLiveData.value!!)
    }

    @Test
    fun `start auth with OPEN_PASSPHRASE action`() {
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)

        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
            assertEquals(R.string.pincode_enter_pin_code, it)
        }
        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
            assertTrue(it)
        }

        assertNotNull(pinCodeViewModel.showFingerPrintEventLiveData.value)
        assertTrue(pinCodeViewModel.backButtonVisibilityLiveData.value!!)
        assertEquals(
            R.string.pincode_enter_pin_code,
            pinCodeViewModel.toolbarTitleResLiveData.value
        )
    }

//    @Test fun `start auth with TIMEOUT_CHECK action and saved pin code in prefs`() {
//        given(interactor.isCodeSet()).willReturn(Single.just(true))
//
//        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)
//
//        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
//            assertEquals(R.string.pincode_enter_pin_code, it)
//        }
//        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
//            assertFalse(it)
//        }
//        assertNotNull(pinCodeViewModel.showFingerPrintEventLiveData.value)
//        assertEquals(R.string.pincode_enter_pin_code, pinCodeViewModel.toolbarTitleResLiveData.value)
//        assertFalse(pinCodeViewModel.backButtonVisibilityLiveData.value!!)
//
//        verify(interactor).isCodeSet()
//        verifyZeroInteractions(progress)
//    }

//    @Test fun `start auth with TIMEOUT_CHECK action and no saved pin code`() {
//        given(interactor.isCodeSet()).willReturn(Single.just(false))
//
//        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)
//
//        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
//            assertEquals(R.string.pincode_set_your_pin_code, it)
//        }
//        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
//            assertFalse(it)
//        }
//        assertEquals(R.string.pincode_set_your_pin_code, pinCodeViewModel.toolbarTitleResLiveData.value)
//        assertFalse(pinCodeViewModel.backButtonVisibilityLiveData.value!!)
//
//        verify(interactor).isCodeSet()
//        verifyZeroInteractions(progress)
//    }

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

//    @Test fun `pin code overclicks`() {
//        given(interactor.checkPin(anyString())).willReturn(Completable.complete())
//
//        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)
//        pinCodeViewModel.pinCodeNumberClicked("1")
//        pinCodeViewModel.pinCodeNumberClicked("2")
//        pinCodeViewModel.pinCodeNumberClicked("3")
//        pinCodeViewModel.pinCodeNumberClicked("4")
//        pinCodeViewModel.pinCodeNumberClicked("5")
//        pinCodeViewModel.pinCodeNumberClicked("6")
//
//        pinCodeViewModel.backButtonVisibilityLiveData.observeForever { }
//        pinCodeViewModel.pinCodeProgressLiveData.observeForever { }
//        pinCodeViewModel.toolbarTitleResLiveData.observeForever { }
//
//        verify(interactor).checkPin("1234")
//        verify(mainRouter).popBackStack()
//        verify(mainRouter).showPassphrase()
//        verifyZeroInteractions(progress)
//    }

    @Test
    fun `pin code entered once while creating`() {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")

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
    fun `pin code entered correctly second time while creating`() {
        given(interactor.savePin(anyString())).willReturn(Completable.complete())
        given(interactor.isBiometryAvailable()).willReturn(Single.just(true))

        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")

        pinCodeViewModel.checkInviteLiveData.observeForever {
            assertNotNull(it)
        }

        verify(interactor).savePin("1234")
        verifyZeroInteractions(progress)
    }

    @Test
    fun `pin code entered wrong second time while creating`() {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("3")

        pinCodeViewModel.toolbarTitleResLiveData.observeForever {
            assertEquals(R.string.pincode_set_your_pin_code, it)
        }

        pinCodeViewModel.backButtonVisibilityLiveData.observeForever {
            assertFalse(it)
        }

        pinCodeViewModel.errorFromResourceLiveData.observeForever {
            assertEquals(R.string.pincode_repeat_error, it.peekContent())
        }

        assertEquals(
            R.string.pincode_set_your_pin_code,
            pinCodeViewModel.toolbarTitleResLiveData.value
        )
        assertFalse(pinCodeViewModel.backButtonVisibilityLiveData.value!!)
    }

    @Test
    fun `pin code check error`() {
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)

        given(interactor.checkPin(anyString())).willReturn(Completable.error(Throwable()))

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")

        pinCodeViewModel.wrongPinCodeEventLiveData.observeForever {
            assertNotNull(it)
        }

        verify(interactor).checkPin(anyString())
    }

    @Test
    fun `pin code entered correct with OPEN_PASSPHRASE action`() {
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)

        given(interactor.checkPin(anyString())).willReturn(Completable.complete())

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")

        verify(interactor).checkPin(anyString())
        verify(mainRouter).showPassphrase()
    }

//    @Test fun `pin code entered correct with TIMEOUT_CHECK action`() {
//        given(interactor.isCodeSet()).willReturn(Single.just(true))
//
//        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)
//
//        given(interactor.checkPin(anyString())).willReturn(Completable.complete())
//
//        pinCodeViewModel.pinCodeNumberClicked("1")
//        pinCodeViewModel.pinCodeNumberClicked("2")
//        pinCodeViewModel.pinCodeNumberClicked("3")
//        pinCodeViewModel.pinCodeNumberClicked("4")
//
//        pinCodeViewModel.checkInviteLiveData.observeForever {
//            assertNotNull(it)
//        }
//
//        verify(interactor).isCodeSet()
//        verify(interactor).checkPin(anyString())
//        verify(mainRouter).showVerification()
//    }

    @Test
    fun `back pressed closing the app on CREATE_PIN_CODE action`() {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)

        pinCodeViewModel.backPressed()

        pinCodeViewModel.closeAppLiveData.observeForever {
            assertNotNull(it)
        }
        assertNotNull(pinCodeViewModel.closeAppLiveData.value)
    }

    @Test
    fun `back pressed closing the app on TIMEOUT_CHECK action`() {
        given(interactor.isCodeSet()).willReturn(Single.just(true))

        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)

        pinCodeViewModel.backPressed()

        pinCodeViewModel.closeAppLiveData.observeForever {
            assertNotNull(it)
        }
        assertNotNull(pinCodeViewModel.closeAppLiveData.value)
    }

    @Test
    fun `back pressed hiding pin code view on OPEN_PASSPHRASE action`() {
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)

        pinCodeViewModel.backPressed()
    }

    @Test
    fun `back pressed leads to reset pin view on CREATE_PIN_CODE action`() {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)

        pinCodeViewModel.pinCodeNumberClicked("1")
        pinCodeViewModel.pinCodeNumberClicked("2")
        pinCodeViewModel.pinCodeNumberClicked("3")
        pinCodeViewModel.pinCodeNumberClicked("4")
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
    fun `onResume() starts fingerprint scanner on OPEN_PASSPHRASE action`() {
        pinCodeViewModel.startAuth(PinCodeAction.CREATE_PIN_CODE)

        pinCodeViewModel.onResume()

        pinCodeViewModel.startFingerprintScannerEventLiveData.observeForever {
            assertNotNull(it)
        }
    }

    @Test
    fun `onResume() starts fingerprint scanner on TIMEOUT_CHECK action`() {
        given(interactor.isCodeSet()).willReturn(Single.just(true))

        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)

        pinCodeViewModel.onResume()

        pinCodeViewModel.startFingerprintScannerEventLiveData.observeForever {
            assertNotNull(it)
        }
    }

    @Test
    fun `fingerprint scanner success leads to passphrase screen on OPEN_PASSPHRASE action`() {
        pinCodeViewModel.startAuth(PinCodeAction.OPEN_PASSPHRASE)

        pinCodeViewModel.onAuthenticationSucceeded()

        verify(mainRouter).showPassphrase()
    }

    @Test
    fun `fingerprint scanner success leads to check user fragment on TIMEOUT_CHECK action`() {
        given(interactor.isCodeSet()).willReturn(Single.just(true))
        pinCodeViewModel.startAuth(PinCodeAction.TIMEOUT_CHECK)
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
}