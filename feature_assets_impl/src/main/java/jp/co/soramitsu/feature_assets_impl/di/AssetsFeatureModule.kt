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

package jp.co.soramitsu.feature_assets_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.domain.QrCodeInteractor
import jp.co.soramitsu.feature_assets_impl.data.AssetsRepositoryImpl
import jp.co.soramitsu.feature_assets_impl.domain.AssetsInteractorImpl
import jp.co.soramitsu.feature_assets_impl.domain.QrCodeInteractorImpl
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import kotlinx.coroutines.FlowPreview

@FlowPreview
@Module
@InstallIn(SingletonComponent::class)
class AssetsFeatureModule {
    @Provides
    fun provideQrScannerContractFactory(implementation: jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QrScannerContractFactoryImpl):
        jp.co.soramitsu.feature_assets_api.presentation.QrScannerContractFactory = implementation


    @Provides
    @Singleton
    fun provideAssetRepository(assetsRepository: AssetsRepositoryImpl): AssetsRepository =
        assetsRepository

    @Singleton
    @Provides
    fun provideAssetsInteractor(
        assetsRepository: AssetsRepository,
        transactionHistoryRepository: TransactionHistoryRepository,
        credentialsRepository: CredentialsRepository,
        userRepository: UserRepository,
        coroutineManager: CoroutineManager,
        transactionBuilder: TransactionBuilder
    ): AssetsInteractor {
        return AssetsInteractorImpl(
            assetsRepository,
            credentialsRepository,
            coroutineManager,
            transactionBuilder,
            transactionHistoryRepository,
            userRepository,
        )
    }

    @Singleton
    @Provides
    fun provideQrCodeInteractor(
        assetsRepository: AssetsRepository,
        userRepository: UserRepository,
        runtimeManager: RuntimeManager
    ): QrCodeInteractor = QrCodeInteractorImpl(
        assetsRepository = assetsRepository,
        userRepository = userRepository,
        runtimeManager = runtimeManager
    )
}
