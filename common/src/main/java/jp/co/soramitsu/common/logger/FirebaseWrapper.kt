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

package jp.co.soramitsu.common.logger

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CancellationException
import timber.log.Timber

object FirebaseWrapper {
    enum class PrivacySafeErrorClass {
        PI_TRANSACTION_PEERS,
        PI_TRANSACTION_HISTORY,
        PI_TRANSACTION_DETAIL,
        EXTRINSIC_SUBMISSION,
        FINALIZED_EVENT_READ,
        NETWORK_FAILURE,
        INVALID_INPUT_FAILURE,
        STATE_FAILURE,
        SECURITY_FAILURE,
        UNCLASSIFIED_FAILURE,
    }

    private val blackList = listOf(CancellationException::class)

    @Volatile
    private var crashlyticsEnabled = false

    fun setCrashlyticsEnabled(enabled: Boolean) {
        crashlyticsEnabled = enabled
    }

    fun recordException(t: Throwable) {
        if (blackList.any { it.isInstance(t) }) {
            return
        }
        recordErrorClass(privacySafeErrorClass(t))
    }

    internal fun privacySafeErrorClass(t: Throwable): PrivacySafeErrorClass = when (t) {
        is SecurityException -> PrivacySafeErrorClass.SECURITY_FAILURE
        is IllegalArgumentException -> PrivacySafeErrorClass.INVALID_INPUT_FAILURE
        is IllegalStateException -> PrivacySafeErrorClass.STATE_FAILURE
        is java.io.IOException -> PrivacySafeErrorClass.NETWORK_FAILURE
        else -> PrivacySafeErrorClass.UNCLASSIFIED_FAILURE
    }

    /**
     * Records an allowlisted class without retaining an originating exception as a cause. Network
     * and signing exceptions may contain addresses, request bodies, or signed payload material.
     */
    fun recordErrorClass(errorClass: PrivacySafeErrorClass) {
        val sanitized = PrivacySafeTelemetryException(errorClass.name)
        Timber.e(sanitized, "ERROR_CLASS")
        crashlyticsInstance()?.recordException(sanitized)
    }

    fun log(message: String) {
        Timber.d(message)
        crashlyticsInstance()?.log(message)
    }

    private fun crashlyticsInstance(): FirebaseCrashlytics? {
        if (!crashlyticsEnabled) {
            return null
        }

        return runCatching { FirebaseCrashlytics.getInstance() }
            .onFailure { crashlyticsEnabled = false }
            .getOrNull()
    }

    private class PrivacySafeTelemetryException(
        errorClass: String,
    ) : IllegalStateException(errorClass)
}
