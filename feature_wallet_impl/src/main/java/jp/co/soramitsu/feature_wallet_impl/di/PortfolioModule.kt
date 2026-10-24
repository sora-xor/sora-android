package jp.co.soramitsu.feature_wallet_impl.di

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioDebugHooks

@Module
@InstallIn(SingletonComponent::class)
interface PortfolioModule {

    /**
     * The Portfolio view's debug switches (sample portfolio, pinned parallax). Bound only in the
     * developDebug source set; every other build, production included, has no binding.
     */
    @BindsOptionalOf
    fun portfolioDebugHooks(): PortfolioDebugHooks
}
