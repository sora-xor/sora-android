/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.request

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class NextAccountIndexRequest(accountAddress: String) : RuntimeRequest(
    method = "system_accountNextIndex",
    params = listOf(
        accountAddress
    )
)