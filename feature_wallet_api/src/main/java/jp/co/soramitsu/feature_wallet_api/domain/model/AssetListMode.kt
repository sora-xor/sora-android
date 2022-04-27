/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import androidx.annotation.StringRes
import jp.co.soramitsu.feature_wallet_api.R

enum class AssetListMode(@StringRes val titleRes: Int) {
    SEND(R.string.select_asset_send),
    RECEIVE(R.string.select_asset_receive),
    SELECT_FOR_LIQUIDITY(R.string.common_select_asset)
}
