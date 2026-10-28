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

package jp.co.soramitsu.feature_wallet_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.POOLS_HUB_NAME
import jp.co.soramitsu.common.domain.SingleFeatureStorageManager
import jp.co.soramitsu.common.nexus.NexusToriiClient
import jp.co.soramitsu.common.nexus.NexusToriiReadClient
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.data.NetworkActivityRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.nexus.DefaultNexusSendQualification
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusFinalityReader
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NetworkActivityRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPendingReconciler
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPendingRecovery
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusSendQualification
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusTransactionSigner
import jp.co.soramitsu.feature_wallet_impl.data.nexus.TairaHttpsConfirmationReader
import jp.co.soramitsu.feature_wallet_impl.data.nexus.TairaTransactionSigner
import jp.co.soramitsu.feature_wallet_impl.data.recovery.Sora2PendingRecoveryScheduler
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.feature_wallet_impl.domain.PoolsFeatureStorageManager
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.Sora2PendingRecoveryKick
import kotlinx.coroutines.FlowPreview

@FlowPreview
@Module
@InstallIn(SingletonComponent::class)
class WalletFeatureModule {

    @Provides
    fun provideNetworkActivityRepository(
        repository: NetworkActivityRepositoryImpl,
    ): NetworkActivityRepository = repository

    @Provides
    @Singleton
    fun provideSora2PendingRecoveryKick(
        scheduler: Sora2PendingRecoveryScheduler,
    ): Sora2PendingRecoveryKick = scheduler

    @Provides
    @Singleton
    fun provideNexusTransactionSigner(credentials: CredentialsRepository, users: UserRepository): NexusTransactionSigner =
        TairaTransactionSigner(credentials, users)

    @Provides
    @Singleton
    fun provideNexusFinalityReader(): NexusFinalityReader =
        TairaHttpsConfirmationReader()

    @Provides
    @Singleton
    fun provideNexusSendQualification(
        signer: NexusTransactionSigner,
        finalityReader: NexusFinalityReader,
    ): NexusSendQualification =
        DefaultNexusSendQualification(signer, finalityReader)

    @Provides
    @Singleton
    fun provideNexusToriiReadClient(
        client: NexusToriiClient,
    ): NexusToriiReadClient = client

    @Provides
    @Singleton
    fun provideNexusPendingRecovery(
        reconciler: NexusPendingReconciler,
    ): NexusPendingRecovery = reconciler

    @Provides
    @Singleton
    fun provideWalletRepository(walletRepositoryImpl: WalletRepositoryImpl): WalletRepository =
        walletRepositoryImpl

    @Provides
    @Singleton
    fun provideWalletDatasource(prefsWalletDatasource: PrefsWalletDatasource): WalletDatasource =
        prefsWalletDatasource

    @Singleton
    @Provides
    fun provideWalletInteractor(
        assetsRepository: AssetsRepository,
        walletRepository: WalletRepository,
        transactionHistoryRepository: TransactionHistoryRepository,
        credentialsRepository: CredentialsRepository,
        userRepository: UserRepository,
        runtimeManager: RuntimeManager,
    ): WalletInteractor {
        return WalletInteractorImpl(
            assetsRepository,
            walletRepository,
            transactionHistoryRepository,
            userRepository,
            credentialsRepository,
            runtimeManager,
        )
    }

    @Singleton
    @Provides
    @IntoMap
    @StringKey(POOLS_HUB_NAME)
    fun providePoolsFeatureStorageManager(
        pools: PoolsFeatureStorageManager
    ): SingleFeatureStorageManager =
        pools
}
