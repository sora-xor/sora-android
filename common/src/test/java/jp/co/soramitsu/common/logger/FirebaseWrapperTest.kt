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
