/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.SoranetApiCreator
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.feature_ethereum_api.EthServiceStarter
import jp.co.soramitsu.feature_ethereum_api.EthStatusPollingServiceStarter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumDatasource
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_impl.EthServiceStarterImpl
import jp.co.soramitsu.feature_ethereum_impl.EthStatusPollingServiceStarterImpl
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthRegisterStateMapper
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthereumConfigMapper
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthereumCredentialsMapper
import jp.co.soramitsu.feature_ethereum_impl.data.network.EthereumNetworkApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.SoranetApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.TransactionFactory
import jp.co.soramitsu.feature_ethereum_impl.data.repository.EthereumRepositoryImpl
import jp.co.soramitsu.feature_ethereum_impl.data.repository.datasource.PrefsEthereumDatasource
import jp.co.soramitsu.feature_ethereum_impl.domain.EthereumInteractorImpl
import jp.co.soramitsu.feature_ethereum_impl.domain.EthereumStatusObserver
import javax.inject.Singleton

@Module
class EthereumFeatureModule {

    @Provides
    @Singleton
    fun provideEthereumRepository(ethereumRepositoryImpl: EthereumRepositoryImpl): EthereumRepository = ethereumRepositoryImpl

    @Provides
    @Singleton
    fun provideEthereumCredentialsMapper(): EthereumCredentialsMapper = EthereumCredentialsMapper()

    @Provides
    @Singleton
    fun provideEthereumConfigMapper(): EthereumConfigMapper = EthereumConfigMapper()

    @Provides
    @Singleton
    fun provideEthereumDatasource(encryptedPreferences: EncryptedPreferences, preferences: Preferences): EthereumDatasource {
        return PrefsEthereumDatasource(encryptedPreferences, preferences)
    }

    @Provides
    @Singleton
    fun provideEthereumApi(apiCreator: NetworkApiCreator): EthereumNetworkApi {
        return apiCreator.create(EthereumNetworkApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSoranetApi(apiCreator: SoranetApiCreator): SoranetApi {
        return apiCreator.create(SoranetApi::class.java)
    }

    @Singleton
    @Provides
    fun provideTransactionFactory(): TransactionFactory = TransactionFactory()

    @Singleton
    @Provides
    fun provideEthereumInteractor(ethereumRepository: EthereumRepository, didRepository: DidRepository, health: HealthChecker): EthereumInteractor {
        return EthereumInteractorImpl(ethereumRepository, didRepository, health)
    }

    @Singleton
    @Provides
    fun provideEthServiceStarter(contextManager: ContextManager): EthServiceStarter {
        return EthServiceStarterImpl(contextManager)
    }

    @Singleton
    @Provides
    fun provideEthStatusPollingServiceStarter(contextManager: ContextManager): EthStatusPollingServiceStarter {
        return EthStatusPollingServiceStarterImpl(contextManager)
    }

    @Singleton
    @Provides
    fun provideEthereumStatusObserver(
        ethereumRepository: EthereumRepository,
        didRepository: DidRepository
    ): EthereumStatusObserver {
        return EthereumStatusObserver(ethereumRepository, didRepository)
    }

    @Singleton
    @Provides
    fun provideEthRegisterStateMapper(): EthRegisterStateMapper = EthRegisterStateMapper()
}