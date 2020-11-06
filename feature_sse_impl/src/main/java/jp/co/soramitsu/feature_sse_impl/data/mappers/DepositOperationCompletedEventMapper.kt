/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.data.mappers

import jp.co.soramitsu.feature_sse_api.model.DepositOperationCompletedEvent
import jp.co.soramitsu.feature_sse_impl.data.network.model.DepositOperationCompletedEventRemote

class DepositOperationCompletedEventMapper {

    fun map(depositOperationCompletedEventRemote: DepositOperationCompletedEventRemote): DepositOperationCompletedEvent {
        return with(depositOperationCompletedEventRemote) {
            DepositOperationCompletedEvent(timestamp, operationId, assetId, amount, "0x$sidechainHash")
        }
    }
}