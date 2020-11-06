/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint

import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricPrompt
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeViewModel
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class FingerprintCallbackTest {

    @Mock lateinit var pinCodeViewModel: PinCodeViewModel

    private lateinit var fingerprintCallback: FingerprintCallback

    @Before fun setUp() {
        fingerprintCallback = FingerprintCallback(pinCodeViewModel)
    }

    @Test fun `onAuthenticationSucceeded called`() {
        val result = mock(BiometricPrompt.AuthenticationResult::class.java)
        fingerprintCallback.onAuthenticationSucceeded(result)

        verify(pinCodeViewModel).onAuthenticationSucceeded()
    }

    @Test fun `onAuthenticationFailed called`() {
        fingerprintCallback.onAuthenticationFailed()

        verify(pinCodeViewModel).onAuthenticationFailed()
    }

    @Test fun `onAuthenticationError called with error from list`() {
        fingerprintCallback.onAuthenticationError(BiometricConstants.ERROR_CANCELED,"")

        verifyNoMoreInteractions(pinCodeViewModel)
    }
}