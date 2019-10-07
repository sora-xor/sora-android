/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.feature_project_api.domain.interfaces.ProjectDatasource
import jp.co.soramitsu.feature_project_api.domain.interfaces.ProjectRepository
import jp.co.soramitsu.feature_project_impl.data.network.ProjectNetworkApi
import jp.co.soramitsu.feature_project_impl.data.repository.ProjectRepositoryImpl
import jp.co.soramitsu.feature_project_impl.data.repository.datasource.PrefsProjectDatasource
import javax.inject.Singleton

@Module
class ProjectFeatureModule {

    @Provides
    @Singleton
    fun provideProjectRepository(projectRepository: ProjectRepositoryImpl): ProjectRepository = projectRepository

    @Provides
    @Singleton
    fun provideProjectDataSource(prefsProjectDatasource: PrefsProjectDatasource): ProjectDatasource = prefsProjectDatasource

    @Provides
    @Singleton
    fun provideProjectApi(apiCreator: NetworkApiCreator): ProjectNetworkApi {
        return apiCreator.create(ProjectNetworkApi::class.java)
    }
}