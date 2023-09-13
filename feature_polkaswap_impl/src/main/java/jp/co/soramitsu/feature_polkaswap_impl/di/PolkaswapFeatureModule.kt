/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_polkaswap_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapExtrinsicRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapSubscriptionRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkaswapExtrinsicRepositoryImpl
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkaswapRepositoryImpl
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkaswapSubscriptionRepositoryImpl
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
