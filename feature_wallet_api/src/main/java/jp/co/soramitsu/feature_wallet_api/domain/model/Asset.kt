/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

data class Asset(
    val id: String,
    val assetFirstName: String,
    val assetLastName: String,
    val displayAsset: Boolean,
    val hidingAllowed: Boolean,
    val position: Int,
    val state: State,
    val roundingPrecision: Int,
    var assetBalance: AssetBalance?
) {
    enum class State {
        NORMAL,
        ASSOCIATING,
        ERROR,
        UNKNOWN
    }
}