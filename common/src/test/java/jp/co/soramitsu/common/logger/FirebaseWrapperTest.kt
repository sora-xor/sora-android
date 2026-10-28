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

package jp.co.soramitsu.common.logger

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class FirebaseWrapperTest {

    @Before
    fun setUp() {
        FirebaseWrapper.setCrashlyticsEnabled(false)
    }

    @After
    fun tearDown() {
        FirebaseWrapper.setCrashlyticsEnabled(false)
    }

    @Test
    fun `log and recordException do not reach Firebase when Crashlytics is disabled`() {
        FirebaseWrapper.log(FirebaseWrapper.Breadcrumb.KEYS_CREATED)
        val sensitive = IllegalStateException(
            "phrase seed private key cnSensitiveWalletAddress raw signed payload"
        )
        assertEquals(
            FirebaseWrapper.PrivacySafeErrorClass.STATE_FAILURE,
            FirebaseWrapper.privacySafeErrorClass(sensitive),
        )
        FirebaseWrapper.recordException(sensitive)
        FirebaseWrapper.recordErrorClass(
            FirebaseWrapper.PrivacySafeErrorClass.EXTRINSIC_SUBMISSION
        )
    }

    @Test
    fun `enabled telemetry never forwards original exception messages causes or suppressed data`() {
        val firebase = mock(FirebaseCrashlytics::class.java)
        mockStatic(FirebaseCrashlytics::class.java).use { staticFirebase ->
            staticFirebase.`when`<FirebaseCrashlytics> { FirebaseCrashlytics.getInstance() }.thenReturn(firebase)
            FirebaseWrapper.setCrashlyticsEnabled(true)
            val original = IllegalStateException("wallet seed and signed payload", SecurityException("private key"))
            original.addSuppressed(IllegalArgumentException("account address"))

            FirebaseWrapper.recordException(original)

            val sent = ArgumentCaptor.forClass(Throwable::class.java)
            verify(firebase).recordException(sent.capture())
            assertEquals("STATE_FAILURE", sent.value.message)
            assertNull(sent.value.cause)
            assertTrue(sent.value.suppressed.isEmpty())
        }
    }

    @Test
    fun `breadcrumbs contain only fixed labels and cancellation is not reported`() {
        val firebase = mock(FirebaseCrashlytics::class.java)
        mockStatic(FirebaseCrashlytics::class.java).use { staticFirebase ->
            staticFirebase.`when`<FirebaseCrashlytics> { FirebaseCrashlytics.getInstance() }.thenReturn(firebase)
            FirebaseWrapper.setCrashlyticsEnabled(true)

            FirebaseWrapper.log(FirebaseWrapper.Breadcrumb.KEYS_CREATED)
            FirebaseWrapper.recordException(CancellationException("sensitive request cancelled"))

            verify(firebase).log("KEYS_CREATED")
            verifyNoMoreInteractions(firebase)
        }
    }
}
