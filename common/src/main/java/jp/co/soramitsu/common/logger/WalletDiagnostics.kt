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

package jp.co.soramitsu.common.logger

import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Privacy-safe diagnostics for wallet flows: fixed breadcrumb labels and
 * allowlisted error classes, never addresses, key names, exception text,
 * balances or transaction payloads.
 *
 * Everything stays on the device (Timber, which debug and develop builds
 * print). Nothing is sent anywhere: the app links no crash or analytics SDK.
 * Crashes and ANRs of store builds reach the developers through Google Play's
 * Android vitals, which needs no library in the app.
 */
object WalletDiagnostics {
    enum class Breadcrumb {
        KEYS_CREATED,
        RETAINED_KEYS_ADOPTED,
        RECOVERY_BACKUP_ADDED,
        ACCOUNT_KEYS_REPAIRED,
        RUNTIME_LEGACY_CACHE_IDENTITY_MISMATCH,
        LEGACY_CREDENTIALS_READ,
        ADDRESS_RECREATED,
        MIGRATION_ATTEMPT_FINISHED,
        SPLASH_NAVIGATION,
        RUNTIME_VERSION_REFRESHED,
        RUNTIME_NETWORK_SELECTED,
        RUNTIME_SNAPSHOT_BUILT,
    }

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

    /** No addresses, key names, exception text, balances, or transaction payloads are accepted. */
    enum class WalletRecoveryEvent {
        GATE_STORAGE_FULL, GATE_DATABASE_LOCKED, GATE_DATABASE_IO, GATE_DATABASE_OPEN_FAILED,
        GATE_DATABASE_PATH_UNREADABLE, GATE_PREFERENCES_INVALID, GATE_KEYSTORE_UNAVAILABLE,
        GATE_SOURCE_CHANGED, GATE_OTHER,
        JOURNAL_RECOVERED, READ_ONLY_ENTERED, ACCOUNT_READ_ONLY,
        PANEL_STORAGE, PANEL_TRANSIENT, PANEL_KEYS_UNAVAILABLE, PANEL_RECOVERY, PANEL_RESTORE_PENDING,
    }

    /** Where diagnostics go; tests replace it to inspect what would be written. */
    @Volatile
    internal var sink: (priority: Int, error: Throwable?, label: String) -> Unit = { priority, error, label ->
        Timber.log(priority, error, label)
    }

    private val recoveryEvents = java.util.Collections.synchronizedSet(mutableSetOf<WalletRecoveryEvent>())

    private val blackList = listOf(CancellationException::class)

    /** One fixed entry per event per process. */
    fun recordWalletRecovery(event: WalletRecoveryEvent) {
        if (!recoveryEvents.add(event)) return
        sink(android.util.Log.WARN, PrivacySafeTelemetryException("WALLET_RECOVERY_${event.name}"), "WALLET_RECOVERY")
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
        sink(android.util.Log.ERROR, PrivacySafeTelemetryException(errorClass.name), "ERROR_CLASS")
    }

    // Only fixed labels are written; wallet data cannot be interpolated.
    fun log(breadcrumb: Breadcrumb) {
        sink(android.util.Log.DEBUG, null, breadcrumb.name)
    }

    internal class PrivacySafeTelemetryException(
        errorClass: String,
    ) : IllegalStateException(errorClass)
}
