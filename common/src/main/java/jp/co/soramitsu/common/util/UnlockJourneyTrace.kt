/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
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

import android.os.Build
import android.os.Trace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import java.util.concurrent.atomic.AtomicInteger

/**
 * Trace sections for the way an existing wallet opens: the app starts, the PIN pad is drawn, the PIN is checked, the
 * Wallet home is drawn with balances. The startup benchmark (module `baselineprofile`) reads them from the system
 * trace. Each section is recorded at most once per process; outside a trace they cost an atomic read. They record
 * no wallet data, only that a step happened.
 */
object UnlockJourneyTrace {

    /** From the process attaching to the first frame of the PIN pad. */
    const val LAUNCH_TO_PIN_PAD = "soraLaunchToPinPad"

    /** From the last PIN digit to the PIN's answer. */
    const val PIN_CHECK = "soraPinCheck"

    /** From the accepted PIN to the first frame of the Wallet home with its balances. */
    const val UNLOCK_TO_HOME = "soraUnlockToHome"

    private val launchToPinPad = Section(LAUNCH_TO_PIN_PAD, cookie = 11)
    private val pinCheck = Section(PIN_CHECK, cookie = 12)
    private val unlockToHome = Section(UNLOCK_TO_HOME, cookie = 13)

    fun processAttached() = launchToPinPad.begin()

    fun pinPadDrawn() = launchToPinPad.end()

    fun pinEntered() = pinCheck.begin()

    /** The PIN's answer. Only an accepted unlock PIN starts the wait for the home. */
    fun pinAnswered(acceptedForUnlock: Boolean) {
        pinCheck.end()
        if (acceptedForUnlock) unlockToHome.begin()
    }

    fun homeDrawn() = unlockToHome.end()

    private class Section(private val name: String, private val cookie: Int) {
        // 0 never begun, 1 open, 2 done: each section is recorded once per process.
        private val state = AtomicInteger(0)

        fun begin() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && state.compareAndSet(0, 1)) {
                Trace.beginAsyncSection(name, cookie)
            }
        }

        fun end() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && state.compareAndSet(1, 2)) {
                Trace.endAsyncSection(name, cookie)
            }
        }
    }
}

/** Runs [action] once, on the frame after this composable's first frame was drawn. */
@Composable
fun OnFirstFrameDrawn(action: () -> Unit) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        action()
    }
}
