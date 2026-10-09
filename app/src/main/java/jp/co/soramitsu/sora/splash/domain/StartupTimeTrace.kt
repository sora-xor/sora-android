/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.sora.splash.domain

import android.os.Build
import android.os.Trace
import java.util.concurrent.atomic.AtomicBoolean

/** Records the update-sensitive path from process attachment through safe splash navigation. */
internal object StartupTimeTrace {

    const val SECTION_NAME = "soraStartupToSafeRoute"

    private const val SECTION_COOKIE = 1
    private val active = AtomicBoolean(false)

    fun begin() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && active.compareAndSet(false, true)) {
            Trace.beginAsyncSection(SECTION_NAME, SECTION_COOKIE)
        }
    }

    fun end() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && active.compareAndSet(true, false)) {
            Trace.endAsyncSection(SECTION_NAME, SECTION_COOKIE)
        }
    }
}
