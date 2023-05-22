/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionMappers
import jp.co.soramitsu.feature_blockexplorer_impl.data.TransactionHistoryRepositoryImpl
import jp.co.soramitsu.feature_blockexplorer_impl.domain.TransactionBuilderImpl
import jp.co.soramitsu.feature_blockexplorer_impl.domain.TransactionHistoryHandlerImpl
import jp.co.soramitsu.feature_blockexplorer_impl.presentation.txhistory.TransactionMappersImpl

@Module
@InstallIn(SingletonComponent::class)
class FeatureBlockExplorerModule {

    @Singleton
    @Provides
    fun provideTransactionHistoryRepository(
        impl: TransactionHistoryRepositoryImpl,
    ): TransactionHistoryRepository {
        return impl
    }

    @Provides
    fun provideTransactionHistoryHandler(
        impl: TransactionHistoryHandlerImpl,
    ): TransactionHistoryHandler {
        return impl
    }

    @Singleton
    @Provides
    fun provideTransactionBuilder(
        impl: TransactionBuilderImpl,
    ): TransactionBuilder {
        return impl
    }

    @Singleton
    @Provides
    fun provideTransactionMappers(
        impl: TransactionMappersImpl,
    ): TransactionMappers {
        return impl
    }
}
