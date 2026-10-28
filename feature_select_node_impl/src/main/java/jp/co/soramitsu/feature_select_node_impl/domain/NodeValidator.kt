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

package jp.co.soramitsu.feature_select_node_impl.domain

import javax.inject.Inject
import jp.co.soramitsu.common.network.SoraNodeEndpointPolicy

internal sealed class ValidationEvent {

    object Succeed : ValidationEvent()
    object ProtocolValidationFailed : ValidationEvent()
    object AddressValidationFailed : ValidationEvent()
}

internal class NodeValidator @Inject constructor() {

    private companion object {
        val WS = Regex("^wss?://", RegexOption.IGNORE_CASE)
    }

    fun validate(url: String): ValidationEvent {
        if (!WS.containsMatchIn(url)) {
            return ValidationEvent.ProtocolValidationFailed
        }

        // Use the same URI admission as persisted and remote nodes. Splitting
        // on ':' confuses IPv6, ports, paths and query strings.
        if (!SoraNodeEndpointPolicy.isAllowed(url)) {
            return ValidationEvent.AddressValidationFailed
        }

        return ValidationEvent.Succeed
    }
}
