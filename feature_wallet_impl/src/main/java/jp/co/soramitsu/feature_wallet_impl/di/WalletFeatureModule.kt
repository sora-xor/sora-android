/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.Sora2CoroutineApiCreator
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.network.sorascan.SoraScanApi
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApi
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApiImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.PolkaswapRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.feature_wallet_impl.domain.PolkaswapInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import javax.inject.Singleton

@Module
class WalletFeatureModule {

    @ExperimentalPagingApi
    @Provides
    @Singleton
    fun provideWalletRepository(walletRepositoryImpl: WalletRepositoryImpl): WalletRepository =
        walletRepositoryImpl

    @Provides
    @Singleton
    fun provideSubstrateApi(api: SubstrateApiImpl): SubstrateApi = api

    @Provides
    @Singleton
    fun provideWalletDatasource(prefsWalletDatasource: PrefsWalletDatasource): WalletDatasource =
        prefsWalletDatasource

    @Singleton
    @Provides
    fun provideQrCodeDecoder(context: Context): QrCodeDecoder {
        return QrCodeDecoder(context.contentResolver)
    }

    @Singleton
    @Provides
    fun provideWalletInteractor(
        walletRepository: WalletRepository,
        ethRepository: EthereumRepository,
        credentialsRepository: CredentialsRepository,
        userRepository: UserRepository,
        cryptoAssistant: CryptoAssistant,
        coroutineManager: CoroutineManager,
    ): WalletInteractor {
        return WalletInteractorImpl(
            walletRepository,
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
        walletRepository: WalletRepository,
        coroutineManager: CoroutineManager,
        polkaswapRepository: PolkaswapRepository,
    ): PolkaswapInteractor {
        return PolkaswapInteractorImpl(
            credentialsRepository,
            coroutineManager,
            polkaswapRepository,
            walletRepository,
        )
    }

    @Singleton
    @Provides
    fun provideSoraScanApi(sora2CoroutineApiCreator: Sora2CoroutineApiCreator): SoraScanApi =
        sora2CoroutineApiCreator.create(SoraScanApi::class.java)
}
