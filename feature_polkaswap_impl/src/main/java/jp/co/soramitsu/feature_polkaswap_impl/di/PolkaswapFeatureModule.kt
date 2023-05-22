/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkaswapRepositoryImpl
import jp.co.soramitsu.feature_polkaswap_impl.domain.PoolsInteractorImpl
import jp.co.soramitsu.feature_polkaswap_impl.domain.SwapInteractorImpl
import kotlinx.coroutines.FlowPreview

@FlowPreview
@Module
@InstallIn(SingletonComponent::class)
class PolkaswapFeatureModule {

    @Provides
    @Singleton
    fun providePolkaswapRepository(impl: PolkaswapRepositoryImpl): PolkaswapRepository = impl

    @Singleton
    @Provides
    fun providePolkaswapInteractor(
        credentialsRepository: CredentialsRepository,
        userRepository: UserRepository,
        transactionHistoryRepository: TransactionHistoryRepository,
        polkaswapRepository: PolkaswapRepository,
        transactionBuilder: TransactionBuilder,
    ): PoolsInteractor {
        return PoolsInteractorImpl(
            credentialsRepository,
            userRepository,
            transactionHistoryRepository,
            polkaswapRepository,
            transactionBuilder,
        )
    }

    @Singleton
    @Provides
    fun provideSwapInteractor(
        assetsRepository: AssetsRepository,
        credentialsRepository: CredentialsRepository,
        userRepository: UserRepository,
        transactionHistoryRepository: TransactionHistoryRepository,
        polkaswapRepository: PolkaswapRepository,
        transactionBuilder: TransactionBuilder,
    ): SwapInteractor {
        return SwapInteractorImpl(
            assetsRepository,
            credentialsRepository,
            userRepository,
            transactionHistoryRepository,
            polkaswapRepository,
            transactionBuilder,
        )
    }
}
