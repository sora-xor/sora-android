/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.data.mappers

import jp.co.soramitsu.feature_sse_api.model.OperationFailedEvent
import jp.co.soramitsu.feature_sse_impl.data.network.model.OperationFailedEventRemote

class OperationFailedEventMapper {

    fun map(operationFailedEventRemote: OperationFailedEventRemote): OperationFailedEvent {
        return with(operationFailedEventRemote) {
            OperationFailedEvent(timestamp, operationId, reason)
        }
    }
}