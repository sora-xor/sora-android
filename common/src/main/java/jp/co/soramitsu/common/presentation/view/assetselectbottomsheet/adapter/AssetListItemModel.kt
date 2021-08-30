/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter

import androidx.annotation.DrawableRes

data class AssetListItemModel(
    @DrawableRes val icon: Int,
    val title: String,
    val amount: String,
    val tokenName: String,
    val sortOrder: Int,
    val assetId: String
)
