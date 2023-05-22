/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.launcher

import jp.co.soramitsu.common.domain.Token

interface WalletRouter {

    fun showValTransferAmount(recipientId: String, assetId: String)

    fun returnToHubFragment()

    fun popBackStackFragment()

    fun showContactsFilled(tokenId: String, address: String)

    fun showAssetSettings()

    fun returnToAddLiquidity(tokenFrom: Token? = null, tokenTo: Token? = null)
}
