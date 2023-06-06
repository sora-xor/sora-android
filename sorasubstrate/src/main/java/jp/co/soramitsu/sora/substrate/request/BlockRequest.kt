/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.request

import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest

class BlockRequest(blockHash: String) : RuntimeRequest("chain_getBlock", listOf(blockHash))
