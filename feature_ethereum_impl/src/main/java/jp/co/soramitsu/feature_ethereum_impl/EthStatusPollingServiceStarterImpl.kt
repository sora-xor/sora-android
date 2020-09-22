/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl

import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.feature_ethereum_api.EthStatusPollingServiceStarter
import jp.co.soramitsu.feature_ethereum_impl.presentation.polling.EthereumStatusPollingService

class EthStatusPollingServiceStarterImpl(
    private val contextManager: ContextManager
) : EthStatusPollingServiceStarter {

    override fun startEthStatusPollingServiceService() {
        EthereumStatusPollingService.start(contextManager.getContext())
    }

    override fun stopEthStatusPollingServiceService() {
        EthereumStatusPollingService.stop(contextManager.getContext())
    }
}