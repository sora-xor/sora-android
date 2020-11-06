package jp.co.soramitsu.feature_ethereum_api.domain.model

data class EthRegisterState(
    val state: State,
    val transactionHash: String?
) {

    enum class State {
        NONE,
        IN_PROGRESS,
        REGISTERED,
        FAILED
    }
}