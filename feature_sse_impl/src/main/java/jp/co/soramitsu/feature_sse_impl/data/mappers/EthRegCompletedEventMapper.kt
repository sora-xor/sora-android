/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.data.mappers

import jp.co.soramitsu.feature_sse_api.model.EthRegistrationCompletedEvent
import jp.co.soramitsu.feature_sse_impl.data.network.model.EthRegistrationCompletedEventRemote

class EthRegCompletedEventMapper {

    fun map(ethRegistrationCompletedEventRemote: EthRegistrationCompletedEventRemote): EthRegistrationCompletedEvent {
        return with(ethRegistrationCompletedEventRemote) {
            EthRegistrationCompletedEvent(timestamp, operationId)
        }
    }
}