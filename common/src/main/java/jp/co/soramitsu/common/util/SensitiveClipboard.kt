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

package jp.co.soramitsu.common.util

import androidx.core.content.edit
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import jp.co.soramitsu.common.domain.AppStateProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Explicit secret copies expire without ever clearing a newer clipboard item. */
@Singleton
class SensitiveClipboard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coroutines: CoroutineManager,
    private val appState: AppStateProvider,
) {
    // Only a random ownership marker and expiry, never the phrase/seed. Survives a process being
    // killed while Android denies clipboard reads in the background.
    private val pending = context.getSharedPreferences("sora_secret_clipboard_expiry", Context.MODE_PRIVATE)

    private var expiryJob: Job? = null

    init {
        coroutines.applicationScope.launch(coroutines.main) {
            appState.observeState().collect { event ->
                if (event == AppStateProvider.AppEvent.ON_RESUME) resumePendingExpiry()
            }
        }
    }

    fun copy(value: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val token = UUID.randomUUID().toString()
        val clip = ClipData.newPlainText("", value)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
            putString(OWNER_KEY, token)
        }
        val issued = SystemClock.elapsedRealtime()
        val editor = pending.edit()
        editor.putString("token", token).putLong("issued", issued).putLong("deadline", issued + 45_000)
        if (!editor.commit()) throw IllegalStateException("Unable to persist clipboard expiry")
        clipboard.setPrimaryClip(clip)
        resumePendingExpiry()
    }

    private fun resumePendingExpiry() {
        expiryJob?.cancel()
        if (!appState.isForeground || !pending.contains("token")) return
        val now = SystemClock.elapsedRealtime()
        val remaining = if (now < pending.getLong("issued", 0)) 0L
            else (pending.getLong("deadline", 0) - now).coerceAtLeast(0L)
        if (remaining == 0L) clearExpiredOwnedCopy()
        else expiryJob = coroutines.applicationScope.launch(coroutines.main) {
            delay(remaining)
            clearExpiredOwnedCopy()
        }
    }

    private fun clearExpiredOwnedCopy() {
        if (!appState.isForeground) return
        val token = pending.getString("token", null) ?: return
        val now = SystemClock.elapsedRealtime()
        // A monotonic-clock reset means the phone restarted: that old copy is already expired.
        if (now >= pending.getLong("issued", 0) && now < pending.getLong("deadline", 0)) return
        runCatching {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val description = clipboard.primaryClipDescription ?: return // retry on next foreground
            if (description.extras?.getString(OWNER_KEY) == token) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            // A newer visible item is left alone. Denied/null reads retain the pending marker.
            pending.edit(commit = true) { clear() }
        }
    }

    private companion object { const val OWNER_KEY = "sora.secret_copy_owner" }
}
