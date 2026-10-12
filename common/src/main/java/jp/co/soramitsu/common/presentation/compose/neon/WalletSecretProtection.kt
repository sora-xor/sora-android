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

package jp.co.soramitsu.common.presentation.compose.neon

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.WeakHashMap

private data class SecureWindowLease(var count: Int, val originallySecure: Boolean)
private val secureWindows = WeakHashMap<Window, SecureWindowLease>()

/** Reference counted across overlapping Compose destinations; never clears an existing flag. */
@Composable
fun ProtectWalletSecrets() {
    val activityWindow = LocalContext.current.secretActivity()?.window
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    val windows = listOfNotNull(activityWindow, dialogWindow).distinct()
    DisposableEffect(windows) {
        windows.forEach { window ->
            val lease = secureWindows.getOrPut(window) {
                SecureWindowLease(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            lease.count++
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            windows.forEach { window -> secureWindows[window]?.let { lease ->
                lease.count--
                if (lease.count == 0) {
                    secureWindows.remove(window)
                    if (!lease.originallySecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            } }
        }
    }
}

/** No saved-state secret or reveal bit: leaving the app and a one-minute timeout both veil it. */
@Composable
fun rememberWalletSecretReveal(identity: Any): MutableState<Boolean> {
    ProtectWalletSecrets()
    val revealed = remember(identity) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, revealed) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) revealed.value = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); revealed.value = false }
    }
    LaunchedEffect(revealed.value) {
        if (revealed.value) { delay(60_000); revealed.value = false }
    }
    return revealed
}

private tailrec fun Context.secretActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.secretActivity()
    else -> null
}
