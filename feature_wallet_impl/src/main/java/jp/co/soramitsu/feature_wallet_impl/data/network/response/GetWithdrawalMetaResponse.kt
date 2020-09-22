package jp.co.soramitsu.feature_wallet_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType

data class GetWithdrawalMetaResponse(
    @SerializedName("status") val status: StatusDto,
    @SerializedName("providerAccountId") val providerAccountId: String,
    @SerializedName("feeAccountId") val feeAccountId: String?,
    @SerializedName("feeRate") val feeRate: String,
    @SerializedName("feeType") val feeType: FeeType
)