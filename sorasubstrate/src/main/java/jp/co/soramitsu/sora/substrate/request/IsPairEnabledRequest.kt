/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.request

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider

class IsPairEnabledRequest(
    inputAssetId: String,
    outputAssetId: String,
    dexId: Int = SubstrateOptionsProvider.dexId,
) : RuntimeRequest("tradingPair_isPairEnabled", listOf(dexId, inputAssetId, outputAssetId))
