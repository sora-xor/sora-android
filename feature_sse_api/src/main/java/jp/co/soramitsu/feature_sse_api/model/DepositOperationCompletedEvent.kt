/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_api.model

data class DepositOperationCompletedEvent(
    val timestamp: Long,
    val operationId: String,
    val assetId: String,
    val amount: Double,
    val sidechainHash: String
) : Event() {

    override fun getEventType(): Type {
        return Type.DEPOSIT_OPERATION_COMPLETED
    }
}