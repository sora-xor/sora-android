/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.model

data class AssetConfigurableModel(
    val id: String,
    val assetFirstName: String,
    val assetLastName: String,
    val assetIconResource: Int,
    val assetIconBackgroundColor: Int,
    val changeCheckStateEnabled: Boolean,
    val checked: Boolean,
    val balance: String?,
    val state: State?
) {

    var position: Int = 0

    enum class State {
        NORMAL,
        ASSOCIATING,
        ERROR
    }
}