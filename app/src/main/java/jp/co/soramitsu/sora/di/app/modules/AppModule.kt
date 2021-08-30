package jp.co.soramitsu.sora.di.app.modules

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.credentials.repository.CredentialsRepositoryImpl
import jp.co.soramitsu.common.data.credentials.repository.datasource.PrefsCredentialsDatasource
import jp.co.soramitsu.common.domain.credentials.CredentialsDatasource
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.sora.SoraApp
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

    @Provides
    @Singleton
    fun provideCredentialsRepository(credentialsRepositoryImpl: CredentialsRepositoryImpl): CredentialsRepository =
        credentialsRepositoryImpl

    @Provides
    @Singleton
    fun provideCredentialsDatasource(
        encryptedPreferences: EncryptedPreferences,
        preferences: Preferences
    ): CredentialsDatasource {
        return PrefsCredentialsDatasource(encryptedPreferences, preferences)
    }
}
