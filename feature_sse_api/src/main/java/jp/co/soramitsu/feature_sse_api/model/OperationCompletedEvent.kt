/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_api.model

data class OperationCompletedEvent(
    val timestamp: Long,
    val operationId: String,
    val type: OperationCompletedEvent.OperationType
) : Event() {

    enum class OperationType {
        OUTGOING,
        INCOMING,
        WITHDRAW,
        REWARD
    }

    override fun getEventType(): Type {
        return Type.OPERATION_COMPLETED
    }
}