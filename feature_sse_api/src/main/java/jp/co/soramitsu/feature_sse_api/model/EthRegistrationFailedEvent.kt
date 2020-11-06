package jp.co.soramitsu.feature_sse_api.model

data class EthRegistrationFailedEvent(
    val timestamp: Long,
    val operationId: String,
    val reason: String?
) : Event() {

    override fun getEventType(): Type {
        return Type.ETH_REGISTRATION_FAILED
    }
}