/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app.modules

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.did.network.DidNetworkApi
import jp.co.soramitsu.common.data.did.repository.DidRepositoryImpl
import jp.co.soramitsu.common.data.did.repository.datasource.PrefsDidDatasource
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.domain.did.DidDatasource
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.feature_main_impl.domain.AccountSettingsImpl
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.AccountSettings
import jp.co.soramitsu.sora.SoraApp
import jp.co.soramitsu.sora.sdk.json.JsonUtil
import javax.inject.Singleton

@Module
class AppModule {

    @Singleton
    @Provides
    fun provideApplication(application: SoraApp): Application {
        return application
    }

    @Singleton
    @Provides
    fun provideContext(application: SoraApp): Context {
        return application
    }

    @Singleton
    @Provides
    fun provideResourceManager(contextManager: ContextManager): ResourceManager {
        return ResourceManager(contextManager)
    }

    @Singleton
    @Provides
    fun provideAccountSettings(didRepository: DidRepository): AccountSettings {
        return AccountSettingsImpl(didRepository)
    }

    @Provides
    @Singleton
    fun provideDidRepository(didRepositoryImpl: DidRepositoryImpl): DidRepository = didRepositoryImpl

    @Provides
    @Singleton
    fun provideDidApi(apiCreator: NetworkApiCreator): DidNetworkApi {
        return apiCreator.create(DidNetworkApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDidDatasource(encryptedPreferences: EncryptedPreferences, cryptoAssistant: CryptoAssistant): DidDatasource {
        return PrefsDidDatasource(encryptedPreferences, cryptoAssistant, JsonUtil.buildMapper())
    }
}