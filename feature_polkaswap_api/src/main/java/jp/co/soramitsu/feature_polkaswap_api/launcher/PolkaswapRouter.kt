/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_api.launcher

import jp.co.soramitsu.common.util.StringPair

interface PolkaswapRouter {

    fun showPoolDetails(ids: StringPair)

    fun showPoolSettings()

    fun showFullPoolsSettings()

    fun showAddLiquidity(tokenFrom: String, tokenTo: String? = null)

    fun showRemoveLiquidity(ids: StringPair)

    fun showSwap(tokenFromId: String? = null, tokenToId: String? = null)
}
