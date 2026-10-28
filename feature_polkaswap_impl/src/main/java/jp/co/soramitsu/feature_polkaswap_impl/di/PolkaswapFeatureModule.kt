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

package jp.co.soramitsu.feature_polkaswap_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapExtrinsicRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapSubscriptionRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsUpdateSubscription
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkaswapExtrinsicRepositoryImpl
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkaswapRepositoryImpl
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkaswapSubscriptionRepositoryImpl
import jp.co.soramitsu.feature_polkaswap_impl.domain.PoolsInteractorImpl
import jp.co.soramitsu.feature_polkaswap_impl.domain.PoolsUpdateSubscriptionImpl
import jp.co.soramitsu.feature_polkaswap_impl.domain.SwapInteractorImpl
import kotlinx.coroutines.FlowPreview

@FlowPreview
@Module
@InstallIn(SingletonComponent::class)
class PolkaswapFeatureModule {

    @Provides
    @Singleton
    fun providePolkaswapRepository(impl: PolkaswapRepositoryImpl): PolkaswapRepository = impl

    @Provides
    @Singleton
    fun providePolkaswapExtrinsicRepository(impl: PolkaswapExtrinsicRepositoryImpl): PolkaswapExtrinsicRepository = impl

    @Provides
    @Singleton
    fun providePolkaswapSubscriptionRepository(impl: PolkaswapSubscriptionRepositoryImpl): PolkaswapSubscriptionRepository = impl

    @Singleton
    @Provides
    fun providePolkaswapInteractor(
        credentialsRepository: CredentialsRepository,
        userRepository: UserRepository,
        transactionHistoryRepository: TransactionHistoryRepository,
        polkaswapRepository: PolkaswapRepository,
        polkaswapExtrinsicRepository: PolkaswapExtrinsicRepository,
        polkaswapSubscriptionRepository: PolkaswapSubscriptionRepository,
        transactionBuilder: TransactionBuilder,
        assetsRepository: AssetsRepository,
    ): PoolsInteractor {
        return PoolsInteractorImpl(
            credentialsRepository,
            userRepository,
            transactionHistoryRepository,
            polkaswapRepository,
            polkaswapSubscriptionRepository,
            polkaswapExtrinsicRepository,
            assetsRepository,
            transactionBuilder,
        )
    }

    @Provides
    @Singleton
    internal fun providePoolsUpdateSubscription(
        repo: PolkaswapSubscriptionRepository,
        manager: CoroutineManager,
    ): PoolsUpdateSubscription {
        return PoolsUpdateSubscriptionImpl(
            repo,
            manager,
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
        polkaswapExtrinsicRepository: PolkaswapExtrinsicRepository,
        polkaswapSubscriptionRepository: PolkaswapSubscriptionRepository,
        transactionBuilder: TransactionBuilder,
    ): SwapInteractor {
        return SwapInteractorImpl(
            assetsRepository,
            credentialsRepository,
            userRepository,
            transactionHistoryRepository,
            polkaswapRepository,
            polkaswapExtrinsicRepository,
            polkaswapSubscriptionRepository,
            transactionBuilder,
        )
    }
}
