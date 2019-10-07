/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.network.WalletNetworkApi
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
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
}