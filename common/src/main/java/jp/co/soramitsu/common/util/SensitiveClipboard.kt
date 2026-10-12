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

package jp.co.soramitsu.common.util

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
        check(pending.edit().putString("token", token).putLong("issued", issued)
            .putLong("deadline", issued + 45_000).commit())
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
            pending.edit().clear().commit()
        }
    }

    private companion object { const val OWNER_KEY = "sora.secret_copy_owner" }
}
