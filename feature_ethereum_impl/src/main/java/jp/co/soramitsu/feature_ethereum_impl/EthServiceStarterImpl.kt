package jp.co.soramitsu.feature_ethereum_impl

import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.feature_ethereum_api.EthServiceStarter
import jp.co.soramitsu.feature_ethereum_impl.presentation.EthereumService

class EthServiceStarterImpl(
    private val contextManager: ContextManager
) : EthServiceStarter {

    override fun startEthService() {
        EthereumService.start(contextManager.getContext())
    }

    override fun startForRetry() {
        EthereumService.startForRetry(contextManager.getContext())
    }
}