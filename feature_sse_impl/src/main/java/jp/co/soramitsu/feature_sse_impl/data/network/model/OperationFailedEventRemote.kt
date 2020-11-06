package jp.co.soramitsu.feature_sse_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class OperationFailedEventRemote(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("operationId") val operationId: String,
    @SerializedName("reason") val reason: String?
) : EventRemote() {

    override fun getEventType(): Type {
        return Type.OperationFailed
    }
}