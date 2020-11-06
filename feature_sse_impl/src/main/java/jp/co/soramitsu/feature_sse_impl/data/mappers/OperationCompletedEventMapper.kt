/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.data.mappers

import jp.co.soramitsu.feature_sse_api.model.OperationCompletedEvent
import jp.co.soramitsu.feature_sse_impl.data.network.model.OperationCompletedEventRemote

class OperationCompletedEventMapper {

    fun map(operationCompletedEventRemote: OperationCompletedEventRemote): OperationCompletedEvent {
        return with(operationCompletedEventRemote) {
            OperationCompletedEvent(timestamp, operationId, mapOperationType(type))
        }
    }

    private fun mapOperationType(typeRemote: OperationCompletedEventRemote.OperationType): OperationCompletedEvent.OperationType {
        return when (typeRemote) {
            OperationCompletedEventRemote.OperationType.INCOMING -> OperationCompletedEvent.OperationType.INCOMING
            OperationCompletedEventRemote.OperationType.OUTGOING -> OperationCompletedEvent.OperationType.OUTGOING
            OperationCompletedEventRemote.OperationType.WITHDRAW -> OperationCompletedEvent.OperationType.WITHDRAW
            OperationCompletedEventRemote.OperationType.REWARD -> OperationCompletedEvent.OperationType.REWARD
        }
    }
}