package jp.co.soramitsu.feature_sora_card_impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.feature_sora_card_impl.domain.SoraCardInteractorImpl

@Module
@InstallIn(SingletonComponent::class)
interface SoraCardModule {

    @Binds
    @Singleton
    fun bindSoraCardInteractor(
        soraCardInteractorImpl: SoraCardInteractorImpl
    ): SoraCardInteractor
}
