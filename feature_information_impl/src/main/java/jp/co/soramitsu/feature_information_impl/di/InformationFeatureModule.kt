/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationDatasource
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationRepository
import jp.co.soramitsu.feature_information_impl.data.network.InformationNetworkApi
import jp.co.soramitsu.feature_information_impl.data.repository.InformationRepositoryImpl
import jp.co.soramitsu.feature_information_impl.data.repository.datasource.PrefsInformationDatasource
import javax.inject.Singleton

@Module
class InformationFeatureModule {

    @Provides
    @Singleton
    fun provideInformationRepository(informationRepository: InformationRepositoryImpl): InformationRepository = informationRepository

    @Provides
    @Singleton
    fun provideInformationDatasource(informationDatasource: PrefsInformationDatasource): InformationDatasource = informationDatasource

    @Provides
    @Singleton
    fun provideInformationNetworkApi(apiCreator: NetworkApiCreator): InformationNetworkApi {
        return apiCreator.create(InformationNetworkApi::class.java)
    }
}