package jp.co.soramitsu.feature_sse_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class OperationStartedEventRemote(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("operationId") val operationId: String,
    @SerializedName("type") val type: OperationType,
    @SerializedName("peerId") val peerId: String,
    @SerializedName("peerName") val peerName: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("details") val details: String,
    @SerializedName("fee") val fee: Double
) : EventRemote() {

    enum class OperationType {
        OUTGOING,
        INCOMING,
        WITHDRAW,
        REWARD
    }

    override fun getEventType(): Type {
        return Type.OperationStarted
    }
}