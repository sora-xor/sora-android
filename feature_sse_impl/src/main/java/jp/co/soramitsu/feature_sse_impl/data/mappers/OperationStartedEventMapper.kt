/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.data.mappers

import jp.co.soramitsu.feature_sse_api.model.OperationStartedEvent
import jp.co.soramitsu.feature_sse_impl.data.network.model.OperationStartedEventRemote

class OperationStartedEventMapper {

    fun map(operationStartedEventRemote: OperationStartedEventRemote): OperationStartedEvent {
        return with(operationStartedEventRemote) {
            OperationStartedEvent(timestamp, operationId, mapOperationType(type), peerId, peerName, amount, details, fee)
        }
    }

    private fun mapOperationType(typeRemote: OperationStartedEventRemote.OperationType): OperationStartedEvent.OperationType {
        return when (typeRemote) {
            OperationStartedEventRemote.OperationType.INCOMING -> OperationStartedEvent.OperationType.INCOMING
            OperationStartedEventRemote.OperationType.OUTGOING -> OperationStartedEvent.OperationType.OUTGOING
            OperationStartedEventRemote.OperationType.WITHDRAW -> OperationStartedEvent.OperationType.WITHDRAW
            OperationStartedEventRemote.OperationType.REWARD -> OperationStartedEvent.OperationType.REWARD
        }
    }
}