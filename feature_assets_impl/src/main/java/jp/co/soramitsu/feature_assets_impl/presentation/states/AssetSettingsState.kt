/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.states

import android.net.Uri

data class AssetSettingsState(
    val id: String,
    val tokenIcon: Uri,
    val tokenName: String,
    val assetAmount: String,
    val symbol: String,
    val favorite: Boolean,
    val visible: Boolean,
    val hideAllowed: Boolean,
    val fiat: Double?,
)
