package jp.co.soramitsu.feature_sse_api.model

data class EthRegistrationCompletedEvent(
    val timestamp: Long,
    val operationId: String
) : Event() {

    override fun getEventType(): Type {
        return Type.ETH_REGISTRATION_COMPLETED
    }
}