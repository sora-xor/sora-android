package jp.co.soramitsu.feature_votable_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.feature_votable_api.domain.interfaces.ProjectRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.ReferendumRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import jp.co.soramitsu.feature_votable_impl.data.local.PrefsVotesDataSource
import jp.co.soramitsu.feature_votable_impl.data.network.ProjectNetworkApi
import jp.co.soramitsu.feature_votable_impl.data.network.ReferendumNetworkApi
import jp.co.soramitsu.feature_votable_impl.data.repository.ProjectRepositoryImpl
import jp.co.soramitsu.feature_votable_impl.data.repository.ReferendumRepositoryImpl
import javax.inject.Singleton

@Module
class VotableFeatureModule {
    @Provides
    @Singleton
    fun provideProjectDataSource(prefsProjectDataSource: PrefsVotesDataSource): VotesDataSource = prefsProjectDataSource

    @Provides
    @Singleton
    fun provideReferendumRepository(referendumRepository: ReferendumRepositoryImpl): ReferendumRepository = referendumRepository

    @Provides
    @Singleton
    fun provideProjectRepository(projectRepository: ProjectRepositoryImpl): ProjectRepository = projectRepository

    @Provides
    @Singleton
    fun provideReferendumApi(apiCreator: NetworkApiCreator): ReferendumNetworkApi {
        return apiCreator.create(ReferendumNetworkApi::class.java)
    }

    @Provides
    @Singleton
    fun provideProjectApi(apiCreator: NetworkApiCreator): ProjectNetworkApi {
        return apiCreator.create(ProjectNetworkApi::class.java)
    }
}