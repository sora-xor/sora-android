package jp.co.soramitsu.feature_ethereum_impl.data.network.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class WithdrawalProof(
    @SerializedName("id") val id: String,
    @SerializedName("txTime") val txTime: Long,
    @SerializedName("blockNum") val blockNum: Int,
    @SerializedName("txIndex") val txIndex: Int,
    @SerializedName("accountIdToNotify") val accountIdToNotify: String,
    @SerializedName("tokenContractAddress") val tokenContractAddress: String,
    @SerializedName("amount") val amount: BigDecimal,
    @SerializedName("relay") val relay: String,
    @SerializedName("irohaTxHash") val irohaTxHash: String,
    @SerializedName("to") val to: String,
    @SerializedName("proofs") val proofs: List<KeccakProof>
)