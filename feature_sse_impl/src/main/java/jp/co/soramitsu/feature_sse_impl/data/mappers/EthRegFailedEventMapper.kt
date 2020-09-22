/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.data.mappers

import jp.co.soramitsu.feature_sse_api.model.EthRegistrationFailedEvent
import jp.co.soramitsu.feature_sse_impl.data.network.model.EthRegistrationFailedEventRemote

class EthRegFailedEventMapper {

    fun map(ethRegistrationFailedEventRemote: EthRegistrationFailedEventRemote): EthRegistrationFailedEvent {
        return with(ethRegistrationFailedEventRemote) {
            EthRegistrationFailedEvent(timestamp, operationId, reason)
        }
    }
}