package jp.co.soramitsu.feature_sse_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class OperationCompletedEventRemote(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("operationId") val operationId: String,
    @SerializedName("type") val type: OperationCompletedEventRemote.OperationType
) : EventRemote() {

    enum class OperationType {
        OUTGOING,
        INCOMING,
        WITHDRAW,
        REWARD
    }

    override fun getEventType(): Type {
        return Type.OperationCompleted
    }
}