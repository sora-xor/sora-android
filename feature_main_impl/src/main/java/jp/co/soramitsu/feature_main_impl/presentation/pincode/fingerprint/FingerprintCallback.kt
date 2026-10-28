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
