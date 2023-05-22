/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.POOLS_HUB_NAME
import jp.co.soramitsu.common.domain.SingleFeatureStorageManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.data.BuyCryptoDataSource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BuyCryptoRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.repository.BuyCryptoRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.BuyCryptoDataSourceImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.feature_wallet_impl.domain.PoolsFeatureStorageManager
import jp.co.soramitsu.feature_wallet_impl.domain.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl
import jp.co.soramitsu.oauth.common.domain.KycRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuHttpClientProvider
import kotlinx.coroutines.FlowPreview

@FlowPreview
@Module
@InstallIn(SingletonComponent::class)
class WalletFeatureModule {

    @Provides
    @Singleton
    fun provideWalletRepository(walletRepositoryImpl: WalletRepositoryImpl): WalletRepository =
        walletRepositoryImpl

    @Provides
    @Singleton
    fun provideWalletDatasource(prefsWalletDatasource: PrefsWalletDatasource): WalletDatasource =
        prefsWalletDatasource

    @Provides
    @Singleton
    fun provideBuyCryptoDataSource(
        clientProvider: SoramitsuHttpClientProvider
    ): BuyCryptoDataSource =
        BuyCryptoDataSourceImpl(clientProvider)

    @Singleton
    @Provides
    fun provideQrCodeDecoder(@ApplicationContext context: Context): QrCodeDecoder {
        return QrCodeDecoder(context.contentResolver)
    }

    @Singleton
    @Provides
    fun provideWalletInteractor(
        assetsRepository: AssetsRepository,
        walletRepository: WalletRepository,
        transactionHistoryRepository: TransactionHistoryRepository,
        credentialsRepository: CredentialsRepository,
        userRepository: UserRepository,
        runtimeManager: RuntimeManager,
        kycRepository: KycRepository
    ): WalletInteractor {
        return WalletInteractorImpl(
            assetsRepository,
            walletRepository,
            transactionHistoryRepository,
            userRepository,
            credentialsRepository,
            runtimeManager,
            kycRepository
        )
    }

    @Provides
    @Singleton
    fun provideBuyCryptoRepository(
        dataSource: BuyCryptoDataSource
    ): BuyCryptoRepository = BuyCryptoRepositoryImpl(
        dataSource
    )

    @Singleton
    @Provides
    @IntoMap
    @StringKey(POOLS_HUB_NAME)
    fun providePoolsFeatureStorageManager(
        pools: PoolsFeatureStorageManager
    ): SingleFeatureStorageManager =
        pools
}
