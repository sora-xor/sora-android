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

package jp.co.soramitsu.common.domain

import java.io.IOException

/**
 * Why a network fee preview could not be shown. Fee quotes, nonces, the signing runtime and the
 * submission all go to the reviewed SORA endpoints (two hosts), not to the node chosen in
 * More > Nodes, so while neither of them can be reached the fee can't be checked and nothing can
 * be signed. Screens show a calm notice with Try again instead of an error dialog.
 */
enum class NetworkFeeUnavailable {
    /** The SORA network could not be reached (offline, timeout, DNS, TLS or server error). */
    NETWORK_UNREACHABLE,

    /**
     * The network was upgraded and now asks for transaction data this app version cannot
     * produce. Trying again does not help; an app update does. Balances still load.
     */
    APP_UPDATE_REQUIRED,

    /** Any other reason. Details go only to the privacy-safe crash channel. */
    OTHER;

    /**
     * Whether asking again can change the answer. It cannot when the app has to be updated, so
     * screens offer the update there instead of Try again.
     */
    val retryCanHelp: Boolean
        get() = this != APP_UPDATE_REQUIRED

    companion object {
        /**
         * Message of the error the signing runtime raises when the live network needs an app
         * update. A fixed code, so that presentation code can recognize it in a cause chain
         * without depending on the substrate module.
         */
        const val APP_UPDATE_REQUIRED_CODE = "SORA2_APP_UPDATE_REQUIRED"

        private const val MAX_CAUSE_DEPTH = 16

        fun from(error: Throwable): NetworkFeeUnavailable {
            var unreachable = false
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < MAX_CAUSE_DEPTH) {
                if (current.message == APP_UPDATE_REQUIRED_CODE) return APP_UPDATE_REQUIRED
                if (current is IOException) unreachable = true
                current = current.cause?.takeIf { it !== current }
                depth += 1
            }
            return if (unreachable) NETWORK_UNREACHABLE else OTHER
        }
    }
}
