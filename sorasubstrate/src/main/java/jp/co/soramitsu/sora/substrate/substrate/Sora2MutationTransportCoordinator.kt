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

package jp.co.soramitsu.sora.substrate.substrate

import java.util.concurrent.atomic.AtomicBoolean
import jp.co.soramitsu.common.network.SoraNodeEndpointPolicy
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
        require(SoraNodeEndpointPolicy.isAllowed(address)) { "SORA_NODE_ENDPOINT_BLOCKED" }
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
        require(SoraNodeEndpointPolicy.isAllowed(address)) { "SORA_NODE_ENDPOINT_BLOCKED" }
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
