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
