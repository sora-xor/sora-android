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

package jp.co.soramitsu.sora.neon

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.util.SensitiveClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SensitiveClipboardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun expiredCopyRetriesOnForegroundAndPreservesANewerItem() {
        rule.setContent { Text("Public clipboard test") }
        val name = "clipboard-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(rule.activity) {
            override fun getSharedPreferences(ignored: String, mode: Int) = rule.activity.getSharedPreferences(name, mode)
        }
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val state = object : AppStateProvider {
            var foreground = true
            val events = MutableStateFlow(AppStateProvider.AppEvent.ON_RESUME)
            override val isForeground get() = foreground
            override val isBackground get() = !foreground
            override fun observeState() = events
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val coroutines = mockk<CoroutineManager>()
        every { coroutines.applicationScope } returns scope
        every { coroutines.main } returns Dispatchers.Main
        lateinit var service: SensitiveClipboard
        try {
            rule.runOnIdle { service = SensitiveClipboard(context, coroutines, state); service.copy("public synthetic phrase") }
            rule.runOnIdle {
                assertTrue(clipboard.primaryClipDescription!!.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
                assertFalse(preferences.all.values.contains("public synthetic phrase"))
                state.foreground = false
                preferences.edit().putLong("deadline", 0).commit()
                state.events.value = AppStateProvider.AppEvent.ON_PAUSE
            }
            rule.runOnIdle { state.events.value = AppStateProvider.AppEvent.ON_RESUME }
            rule.runOnIdle { assertTrue(preferences.contains("token")); state.events.value = AppStateProvider.AppEvent.ON_PAUSE }
            rule.runOnIdle { state.foreground = true; state.events.value = AppStateProvider.AppEvent.ON_RESUME }
            rule.waitUntil { !preferences.contains("token") }
            rule.runOnIdle { assertFalse(clipboard.hasPrimaryClip()) }

            rule.runOnIdle {
                service.copy("second public phrase")
                clipboard.setPrimaryClip(ClipData.newPlainText("newer item", "keep this newer text"))
                preferences.edit().putLong("deadline", 0).commit()
                state.events.value = AppStateProvider.AppEvent.ON_PAUSE
            }
            rule.runOnIdle { state.events.value = AppStateProvider.AppEvent.ON_RESUME }
            rule.waitUntil { !preferences.contains("token") }
            rule.runOnIdle { assertEquals("keep this newer text", clipboard.primaryClip!!.getItemAt(0).text.toString()) }
        } finally {
            scope.cancel()
            rule.runOnIdle { clipboard.clearPrimaryClip(); rule.activity.deleteSharedPreferences(name) }
        }
    }
    @Test fun restartingBeforeExpiryStillClearsWithoutAnotherLifecycleEvent() {
        rule.setContent { Text("Public restart test") }
        val name = "clipboard-restart-${UUID.randomUUID()}"
        val context = object : ContextWrapper(rule.activity) {
            override fun getSharedPreferences(ignored: String, mode: Int) = rule.activity.getSharedPreferences(name, mode)
        }
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val state = object : AppStateProvider {
            override val isForeground = true
            override val isBackground = false
            override fun observeState() = MutableStateFlow(AppStateProvider.AppEvent.ON_RESUME)
        }
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        fun coroutines(scope: CoroutineScope) = mockk<CoroutineManager>().also {
            every { it.applicationScope } returns scope
            every { it.main } returns Dispatchers.Main
        }
        try {
            rule.runOnIdle {
                SensitiveClipboard(context, coroutines(firstScope), state).copy("public restart fixture")
                firstScope.cancel() // Models process loss: no old delayed job survives.
                prefs.edit().putLong("deadline", android.os.SystemClock.elapsedRealtime() + 500).commit()
                SensitiveClipboard(context, coroutines(restartedScope), state)
                assertEquals("public restart fixture", clipboard.primaryClip!!.getItemAt(0).text.toString())
            }
            // No further ON_RESUME or copy: restored deadline must schedule its own remaining delay.
            rule.waitUntil(timeoutMillis = 5_000) { !prefs.contains("token") }
            rule.runOnIdle { assertFalse(clipboard.hasPrimaryClip()) }
        } finally {
            firstScope.cancel(); restartedScope.cancel()
            rule.runOnIdle { clipboard.clearPrimaryClip(); rule.activity.deleteSharedPreferences(name) }
        }
    }

}
