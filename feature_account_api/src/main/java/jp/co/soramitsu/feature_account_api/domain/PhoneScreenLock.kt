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

package jp.co.soramitsu.feature_account_api.domain

/**
 * One pass of the phone's own screen lock (PIN, pattern, password, or a biometric that stands
 * for it), given by the system prompt. Made only by [PhoneScreenLock.confirmed]; it names
 * nothing and cannot be saved or rebuilt.
 */
class ScreenLockConfirmation internal constructor()

/**
 * A new app PIN was refused: the stored one cannot be read while accounts are on the phone, so
 * the phone's screen lock has to be passed first (and recently). Nothing was saved.
 */
class ScreenLockConfirmationRequiredException : Exception("SCREEN_LOCK_CONFIRMATION_REQUIRED")

/**
 * The phone's own screen lock as the proof of ownership when the app PIN cannot give it: the
 * stored PIN is there but cannot be read (damaged data), and accounts are on the phone. Whoever
 * holds the phone may then choose a new PIN only after passing the system screen-lock prompt.
 * A phone without a screen lock has no stronger local proof, so nothing more is asked there.
 *
 * One instance for the whole app: the screen that shows the system prompt records its success
 * here ([confirmed]) and the repository that saves the PIN checks it ([isFresh], [use]), so a
 * save that did not come through the prompt is refused whatever the screen did.
 */
class PhoneScreenLock(
    private val deviceSecure: () -> Boolean,
    /** A clock that also counts while the phone sleeps and cannot be set by the person. */
    private val elapsedMillis: () -> Long,
) {
    private var outstanding: ScreenLockConfirmation? = null
    private var confirmedAt = 0L

    /** True when the phone has a screen lock the system prompt can ask for. */
    fun isSet(): Boolean = runCatching(deviceSecure).getOrDefault(false)

    /**
     * The system prompt just reported success. Called from its success callback and from
     * nowhere else. An earlier confirmation stops counting.
     */
    @Synchronized
    fun confirmed(): ScreenLockConfirmation {
        val confirmation = ScreenLockConfirmation()
        outstanding = confirmation
        confirmedAt = elapsedMillis()
        return confirmation
    }

    /** True while [confirmation] is the latest one and was given within [VALID_MILLIS]. */
    @Synchronized
    fun isFresh(confirmation: ScreenLockConfirmation?): Boolean {
        val latest = outstanding ?: return false
        if (confirmation !== latest) return false
        val age = elapsedMillis() - confirmedAt
        if (age < 0 || age > VALID_MILLIS) {
            outstanding = null
            return false
        }
        return true
    }

    /** [confirmation] did its one job; it does not count again. */
    @Synchronized
    fun use(confirmation: ScreenLockConfirmation?) {
        if (confirmation != null && confirmation === outstanding) outstanding = null
    }

    companion object {
        /** Time to choose and repeat a new PIN after the prompt. */
        const val VALID_MILLIS = 5 * 60 * 1_000L
    }
}
