/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_api.presentation.launcher

interface AssetsRouter {

    fun showTxDetails(txHash: String, pop: Boolean = false)

    fun showTxList(assetId: String)

    fun showContacts(tokenId: String)

    fun showReceive()

    fun showBuyCrypto()

    fun showFullAssetsSettings()

    fun showAssetDetails(assetId: String)

    fun popBackStackFragment()
}
