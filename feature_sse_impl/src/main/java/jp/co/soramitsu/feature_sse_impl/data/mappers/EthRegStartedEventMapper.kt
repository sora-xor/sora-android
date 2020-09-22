/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.data.mappers

import jp.co.soramitsu.feature_sse_api.model.EthRegistrationStartedEvent
import jp.co.soramitsu.feature_sse_impl.data.network.model.EthRegistrationStartedEventRemote

class EthRegStartedEventMapper {

    fun map(ethRegistrationStartedEventRemote: EthRegistrationStartedEventRemote): EthRegistrationStartedEvent {
        return with(ethRegistrationStartedEventRemote) {
            EthRegistrationStartedEvent(timestamp, operationId, address)
        }
    }
}