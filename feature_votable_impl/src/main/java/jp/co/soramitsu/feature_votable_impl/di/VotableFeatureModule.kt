package jp.co.soramitsu.feature_votable_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import jp.co.soramitsu.feature_votable_impl.data.local.PrefsVotesDataSource
import javax.inject.Singleton

@Module
class VotableFeatureModule {
    @Provides
    @Singleton
    fun provideProjectDataSource(prefsProjectDataSource: PrefsVotesDataSource): VotesDataSource = prefsProjectDataSource
}
