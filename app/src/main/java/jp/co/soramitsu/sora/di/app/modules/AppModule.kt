/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app.modules

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_impl.data.repository.CredentialsRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsCredentialsDatasource
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager

@InstallIn(SingletonComponent::class)
@Module
class AppModule {

    @Provides
    @Singleton
    fun provideCredentialsRepository(
        credentialsDatasource: CredentialsDatasource,
        ca: CryptoAssistant,
        runtimeManager: RuntimeManager,
        jsonAccountsEncoder: JsonAccountsEncoder,
        soraConfigManager: SoraConfigManager,
    ): CredentialsRepository =
        CredentialsRepositoryImpl(credentialsDatasource, ca, runtimeManager, jsonAccountsEncoder, soraConfigManager)

    @Provides
    @Singleton
    fun provideCredentialsDatasource(
        encryptedPreferences: EncryptedPreferences,
        soraPreferences: SoraPreferences
    ): CredentialsDatasource {
        return PrefsCredentialsDatasource(encryptedPreferences, soraPreferences)
    }
}
