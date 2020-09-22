package jp.co.soramitsu.feature_ethereum_impl.presentation.di

import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_ethereum_impl.presentation.EthereumService

@Subcomponent
@ScreenScope
interface EthereumServiceComponent {

    @Subcomponent.Builder
    interface Builder {

        fun build(): EthereumServiceComponent
    }

    fun inject(ethereumService: EthereumService)
}