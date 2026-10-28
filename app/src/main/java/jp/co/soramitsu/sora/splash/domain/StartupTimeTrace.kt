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
