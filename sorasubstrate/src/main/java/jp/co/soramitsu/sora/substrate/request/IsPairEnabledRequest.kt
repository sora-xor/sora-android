/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.request

import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest

class IsPairEnabledRequest(
    inputAssetId: String,
    outputAssetId: String,
    dexId: Int,
) : RuntimeRequest("tradingPair_isPairEnabled", listOf(dexId, inputAssetId, outputAssetId))
