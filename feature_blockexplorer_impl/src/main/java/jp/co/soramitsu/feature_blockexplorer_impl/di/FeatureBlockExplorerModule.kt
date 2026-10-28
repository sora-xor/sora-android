/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
