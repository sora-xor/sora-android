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

import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletDiagnosticsTest {

    private data class Entry(val priority: Int, val error: Throwable?, val label: String)

    private val written = mutableListOf<Entry>()
    private lateinit var originalSink: (Int, Throwable?, String) -> Unit

    @Before
    fun setUp() {
        originalSink = WalletDiagnostics.sink
        WalletDiagnostics.sink = { priority, error, label -> written += Entry(priority, error, label) }
    }

    @After
    fun tearDown() {
        WalletDiagnostics.sink = originalSink
    }

    @Test
    fun `exceptions are written as allowlisted classes without message cause or suppressed data`() {
        val original = IllegalStateException("wallet seed and signed payload", SecurityException("private key"))
        original.addSuppressed(IllegalArgumentException("account address"))

        WalletDiagnostics.recordException(original)

        val entry = written.single()
        assertEquals("ERROR_CLASS", entry.label)
        assertEquals("STATE_FAILURE", entry.error?.message)
        assertNull(entry.error?.cause)
        assertTrue(entry.error!!.suppressed.isEmpty())
        assertFalse(written.toString().contains("seed"))
        assertFalse(written.toString().contains("private key"))
    }

    @Test
    fun `error classes follow the exception type`() {
        assertEquals(
            WalletDiagnostics.PrivacySafeErrorClass.SECURITY_FAILURE,
            WalletDiagnostics.privacySafeErrorClass(SecurityException("key")),
        )
        assertEquals(
            WalletDiagnostics.PrivacySafeErrorClass.INVALID_INPUT_FAILURE,
            WalletDiagnostics.privacySafeErrorClass(IllegalArgumentException("address")),
        )
        assertEquals(
            WalletDiagnostics.PrivacySafeErrorClass.NETWORK_FAILURE,
            WalletDiagnostics.privacySafeErrorClass(java.io.IOException("node")),
        )
        assertEquals(
            WalletDiagnostics.PrivacySafeErrorClass.UNCLASSIFIED_FAILURE,
            WalletDiagnostics.privacySafeErrorClass(Throwable("other")),
        )
        WalletDiagnostics.recordErrorClass(WalletDiagnostics.PrivacySafeErrorClass.EXTRINSIC_SUBMISSION)
        assertEquals("EXTRINSIC_SUBMISSION", written.single().error?.message)
    }

    @Test
    fun `breadcrumbs contain only fixed labels and cancellation is not written`() {
        WalletDiagnostics.log(WalletDiagnostics.Breadcrumb.KEYS_CREATED)
        WalletDiagnostics.recordException(CancellationException("sensitive request cancelled"))

        assertEquals(listOf(Entry(android.util.Log.DEBUG, null, "KEYS_CREATED")), written)
    }

    @Test
    fun `each recovery event is written once per process`() {
        WalletDiagnostics.recordWalletRecovery(WalletDiagnostics.WalletRecoveryEvent.PANEL_RESTORE_PENDING)
        WalletDiagnostics.recordWalletRecovery(WalletDiagnostics.WalletRecoveryEvent.PANEL_RESTORE_PENDING)

        val entries = written.filter { it.error?.message == "WALLET_RECOVERY_PANEL_RESTORE_PENDING" }
        assertTrue(entries.size <= 1)
    }
}
