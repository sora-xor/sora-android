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

package jp.co.soramitsu.sora.substrate.substrate

import java.util.concurrent.atomic.AtomicBoolean
import jp.co.soramitsu.sora.substrate.runtime.requireReviewedSora2WebSocketEndpoint
import jp.co.soramitsu.sora.substrate.runtime.reviewedSora2WebSocketEndpointOrNull

/**
 * Pure, synchronized authority for selected-socket mutation admission and deferred switching.
 * Merely requesting the reviewed endpoint never authorizes a mutation: the socket must report an
 * exact matching connected endpoint. During a lease, switch requests are retained and the last
 * lease release revokes admission before the real socket switch callback can run.
 */
internal class Sora2MutationTransportCoordinator(
    private val reviewedEndpointOrNull: (String?) -> String? =
        ::reviewedSora2WebSocketEndpointOrNull,
) {
    private val lock = Any()
    private var requestedAddress: String? = null
    private var admittedConnectedAddress: String? = null
    private var activeLeases: Int = 0
    private var deferredSwitchAddress: String? = null

    fun setAddress(address: String) {
        synchronized(lock) {
            check(activeLeases == 0) { "SORA2_MUTATION_TRANSPORT_ACTIVE" }
            requestedAddress = address
            admittedConnectedAddress = null
        }
    }

    fun requestedAddress(): String? = synchronized(lock) { requestedAddress }

    fun observeConnectedAddress(connectedAddress: String?) {
        synchronized(lock) {
            val reviewedRequested = reviewedEndpointOrNull(requestedAddress)
            val reviewedConnected = reviewedEndpointOrNull(connectedAddress)
            admittedConnectedAddress = reviewedConnected?.takeIf {
                reviewedRequested == reviewedConnected
            }
        }
    }

    /** Returns an address only when the socket switch may happen immediately. */
    fun requestSwitch(address: String): String? = synchronized(lock) {
        if (activeLeases > 0) {
            deferredSwitchAddress = address
            null
        } else {
            requestedAddress = address
            admittedConnectedAddress = null
            address
        }
    }

    fun requireReviewedTransport() {
        synchronized(lock) {
            requireReviewedSora2WebSocketEndpoint(admittedConnectedAddress)
        }
    }

    fun acquire(): Lease {
        synchronized(lock) {
            requireReviewedSora2WebSocketEndpoint(admittedConnectedAddress)
            activeLeases += 1
        }
        return Lease(this)
    }

    private fun release(): String? = synchronized(lock) {
        check(activeLeases > 0) { "SORA2_MUTATION_TRANSPORT_LEASE_INVALID" }
        activeLeases -= 1
        if (activeLeases == 0 && deferredSwitchAddress != null) {
            checkNotNull(deferredSwitchAddress).also { next ->
                deferredSwitchAddress = null
                requestedAddress = next
                admittedConnectedAddress = null
            }
        } else {
            null
        }
    }

    internal class Lease internal constructor(
        private val coordinator: Sora2MutationTransportCoordinator,
    ) {
        private val released = AtomicBoolean(false)

        fun closeAndRunDeferredSwitch(switch: (String) -> Unit) {
            if (released.compareAndSet(false, true)) {
                coordinator.release()?.let(switch)
            }
        }
    }
}
