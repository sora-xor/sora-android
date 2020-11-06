/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.presentation.polling.di

import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_ethereum_impl.presentation.polling.EthereumStatusPollingService

@Subcomponent
@ScreenScope
interface EthereumStatusPollingServiceComponent {

    @Subcomponent.Builder
    interface Builder {

        fun build(): EthereumStatusPollingServiceComponent
    }

    fun inject(ethereumStatusPollingService: EthereumStatusPollingService)
}