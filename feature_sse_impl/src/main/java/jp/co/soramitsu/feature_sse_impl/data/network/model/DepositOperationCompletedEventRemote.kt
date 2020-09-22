package jp.co.soramitsu.feature_sse_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class DepositOperationCompletedEventRemote(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("operationId") val operationId: String,
    @SerializedName("assetId") val assetId: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("sidechainHash") val sidechainHash: String
) : EventRemote() {

    override fun getEventType(): Type {
        return Type.OperationStarted
    }
}