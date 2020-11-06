/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.qr

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.util.Const

data class QrDataRecord(
    @SerializedName("accountId") val accountId: String,
    @SerializedName("amount") val amount: String?,
    @SerializedName("assetId") val assetId: String? = Const.VAL_ASSET_ID
)