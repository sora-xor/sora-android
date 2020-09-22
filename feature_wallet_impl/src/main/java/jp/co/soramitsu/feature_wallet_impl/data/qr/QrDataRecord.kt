package jp.co.soramitsu.feature_wallet_impl.data.qr

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.util.Const

data class QrDataRecord(
    @SerializedName("accountId") val accountId: String,
    @SerializedName("amount") val amount: String?,
    @SerializedName("assetId") val assetId: String? = Const.XOR_ASSET_ID
)