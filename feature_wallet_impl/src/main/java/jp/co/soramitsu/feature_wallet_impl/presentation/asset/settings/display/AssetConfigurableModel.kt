/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display

data class AssetConfigurableModel(
    val id: String,
    val assetFirstName: String,
    val assetLastName: String,
    val assetIconResource: Int,
    val hideAllowed: Boolean,
    val balance: String?,
    var visible: Boolean,
)
