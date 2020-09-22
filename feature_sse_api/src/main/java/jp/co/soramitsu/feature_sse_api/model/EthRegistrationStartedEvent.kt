/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_api.model

data class EthRegistrationStartedEvent(
    val timestamp: Long,
    val operationId: String,
    val address: String
) : Event() {

    override fun getEventType(): Type {
        return Type.ETH_REGISTRATION_STARTED
    }
}