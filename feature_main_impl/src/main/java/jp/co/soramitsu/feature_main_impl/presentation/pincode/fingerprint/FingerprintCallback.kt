/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.ERROR_CANCELED
import androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeViewModel

class FingerprintCallback(private val pinCodeViewModel: PinCodeViewModel) : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationError(errMsgId: Int, errString: CharSequence) {
        if (errMsgId == ERROR_CANCELED ||
            errMsgId == ERROR_NEGATIVE_BUTTON ||
            errMsgId == ERROR_USER_CANCELED
        ) {
            pinCodeViewModel.canceledFromPrompt()
        } else {
            pinCodeViewModel.onAuthenticationError(errString.toString())
        }
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        pinCodeViewModel.onAuthenticationSucceeded()
    }

    override fun onAuthenticationFailed() {
        pinCodeViewModel.onAuthenticationFailed()
    }
}
