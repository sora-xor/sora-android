/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_impl.data.AssetsRepositoryImpl
import jp.co.soramitsu.feature_assets_impl.domain.AssetsInteractorImpl
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import kotlinx.coroutines.FlowPreview

@FlowPreview
@Module
@InstallIn(SingletonComponent::class)
class AssetsFeatureModule {

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
}
