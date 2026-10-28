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
