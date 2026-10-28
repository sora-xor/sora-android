/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.sora.di.app.modules

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_impl.data.repository.CredentialsRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsCredentialsDatasource

@InstallIn(SingletonComponent::class)
@Module
class AppModule {

    @Provides
    @Singleton
    fun provideCredentialsRepository(
        credentialsDatasource: CredentialsDatasource,
        ca: CryptoAssistant,
        sora2AddressCodec: Sora2AddressCodec,
        jsonAccountsEncoder: JsonAccountsEncoder,
    ): CredentialsRepository =
        CredentialsRepositoryImpl(
            credentialsDatasource,
            ca,
            sora2AddressCodec,
            jsonAccountsEncoder,
        )

    @Provides
    @Singleton
    fun provideCredentialsDatasource(
        encryptedPreferences: EncryptedPreferences,
        soraPreferences: SoraPreferences
    ): CredentialsDatasource {
        return PrefsCredentialsDatasource(encryptedPreferences, soraPreferences)
    }
}
