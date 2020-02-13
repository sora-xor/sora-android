/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidDatasource
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository
import jp.co.soramitsu.feature_did_impl.data.network.DidNetworkApi
import jp.co.soramitsu.feature_did_impl.data.repository.DidRepositoryImpl
import jp.co.soramitsu.feature_did_impl.data.repository.datasource.PrefsDidDatasource
import jp.co.soramitsu.sora.sdk.json.JsonUtil
import javax.inject.Singleton

@Module
class DidFeatureModule {

    @Provides
    @Singleton
    fun provideDidRepository(didRepositoryImpl: DidRepositoryImpl): DidRepository = didRepositoryImpl

    @Provides
    @Singleton
    fun provideRegistrationApi(apiCreator: NetworkApiCreator): DidNetworkApi {
        return apiCreator.create(DidNetworkApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDidDatasource(encryptedPreferences: EncryptedPreferences, cryptoAssistant: CryptoAssistant): DidDatasource {
        return PrefsDidDatasource(encryptedPreferences, cryptoAssistant, JsonUtil.buildMapper())
    }
}