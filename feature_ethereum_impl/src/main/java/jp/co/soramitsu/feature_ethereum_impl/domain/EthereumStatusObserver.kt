package jp.co.soramitsu.feature_ethereum_impl.domain

import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository

class EthereumStatusObserver(
    private val ethereumRepository: EthereumRepository,
    private val credentialsRepository: CredentialsRepository
) {

    companion object {
        private const val POLLING_REFRESH_TIME = 15L
    }

    fun release() {
    }
}
