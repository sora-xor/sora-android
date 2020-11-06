/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import android.content.Context
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.AccountSettings
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.network.TransactionFactory
import jp.co.soramitsu.feature_wallet_impl.data.network.WalletNetworkApi
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import javax.inject.Singleton

@Module
class WalletFeatureModule {

    @Provides
    @Singleton
    fun provideWalletRepository(walletRepositoryImpl: WalletRepositoryImpl): WalletRepository = walletRepositoryImpl

    @Provides
    @Singleton
    fun provideWalletApi(apiCreator: NetworkApiCreator): WalletNetworkApi {
        return apiCreator.create(WalletNetworkApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWalletDatasource(prefsWalletDatasource: PrefsWalletDatasource): WalletDatasource = prefsWalletDatasource

    @Singleton
    @Provides
    fun provideQrCodeDecoder(context: Context): QrCodeDecoder {
        return QrCodeDecoder(context.contentResolver)
    }

    @Singleton
    @Provides
    fun provideTransactionFactory(): TransactionFactory = TransactionFactory()

    @Singleton
    @Provides
    fun provideWalletInteractor(walletRepository: WalletRepository, ethRepository: EthereumRepository, accountSettings: AccountSettings): WalletInteractor {
        return WalletInteractorImpl(walletRepository, ethRepository, accountSettings)
    }
}