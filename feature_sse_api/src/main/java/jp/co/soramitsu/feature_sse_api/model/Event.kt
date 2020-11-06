package jp.co.soramitsu.feature_sse_api.model

abstract class Event {

    abstract fun getEventType(): Type

    enum class Type {
        OPERATION_STARTED,
        ETH_REGISTRATION_STARTED,
        ETH_REGISTRATION_COMPLETED,
        ETH_REGISTRATION_FAILED,
        OPERATION_COMPLETED,
        DEPOSIT_OPERATION_COMPLETED,
        OPERATION_FAILED
    }
}