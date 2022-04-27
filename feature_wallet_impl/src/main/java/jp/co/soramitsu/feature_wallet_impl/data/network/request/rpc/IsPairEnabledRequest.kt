/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc

import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class IsPairEnabledRequest(
    inputAssetId: String,
    outputAssetId: String,
    dexId: Int = OptionsProvider.dexId,
) : RuntimeRequest("tradingPair_isPairEnabled", listOf(dexId, inputAssetId, outputAssetId))
