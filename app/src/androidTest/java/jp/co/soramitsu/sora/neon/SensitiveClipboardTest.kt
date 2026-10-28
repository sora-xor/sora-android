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
