/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_api.model

data class OperationStartedEvent(
    val timestamp: Long,
    val operationId: String,
    val type: OperationType,
    val peerId: String,
    val peerName: String,
    val amount: Double,
    val details: String,
    val fee: Double
) : Event() {

    enum class OperationType {
        OUTGOING,
        INCOMING,
        WITHDRAW,
        REWARD
    }

    override fun getEventType(): Type {
        return Type.OPERATION_STARTED
    }
}