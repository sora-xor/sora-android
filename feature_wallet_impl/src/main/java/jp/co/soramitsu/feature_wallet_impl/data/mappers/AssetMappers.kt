/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.data.network.model.AssetRemote

fun mapAssetRemoteToAsset(assetRemote: AssetRemote): Asset {
    return with(assetRemote) {
        Asset(balance, id)
    }
}