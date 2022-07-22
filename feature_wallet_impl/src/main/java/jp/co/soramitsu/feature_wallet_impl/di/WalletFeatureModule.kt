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
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.repository.PolkaswapRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.TransactionHistoryRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.feature_wallet_impl.domain.PolkaswapInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.domain.PoolsManagerImpl
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import kotlinx.coroutines.FlowPreview
import javax.inject.Singleton

@FlowPreview
@Module
@InstallIn(SingletonComponent::class)
class WalletFeatureModule {

    @Provides
    @Singleton
    fun provideTransactionHistoryRepository(repositoryImpl: TransactionHistoryRepositoryImpl): TransactionHistoryRepository =
        repositoryImpl

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
    fun provideQrCodeDecoder(@ApplicationContext context: Context): QrCodeDecoder {
        return QrCodeDecoder(context.contentResolver)
    }

    @Singleton
    @Provides
    fun provideWalletInteractor(
        walletRepository: WalletRepository,
        transactionHistoryRepository: TransactionHistoryRepository,
        ethRepository: EthereumRepository,
        credentialsRepository: CredentialsRepository,
        userRepository: UserRepository,
        cryptoAssistant: CryptoAssistant,
        coroutineManager: CoroutineManager,
    ): WalletInteractor {
        return WalletInteractorImpl(
            walletRepository,
            transactionHistoryRepository,
            ethRepository,
            userRepository,
            credentialsRepository,
            cryptoAssistant,
            coroutineManager
        )
    }

    @Provides
    @Singleton
    fun providePolkaswapRepository(impl: PolkaswapRepositoryImpl): PolkaswapRepository = impl

    @Singleton
    @Provides
    fun providePolkaswapInteractor(
        credentialsRepository: CredentialsRepository,
        userRepository: UserRepository,
        transactionHistoryRepository: TransactionHistoryRepository,
        walletRepository: WalletRepository,
        coroutineManager: CoroutineManager,
        polkaswapRepository: PolkaswapRepository,
    ): PolkaswapInteractor {
        return PolkaswapInteractorImpl(
            credentialsRepository,
            userRepository,
            transactionHistoryRepository,
            coroutineManager,
            polkaswapRepository,
            walletRepository,
        )
    }

    @Singleton
    @Provides
    fun providePoolsManager(polkaswapInteractor: PolkaswapInteractor): PoolsManager {
        return PoolsManagerImpl(polkaswapInteractor)
    }
}
