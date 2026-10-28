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
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeViewModel
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
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
        fingerprintCallback.onAuthenticationError(ERROR_CANCELED, "")

        verify(pinCodeViewModel).canceledFromPrompt()
    }
}
