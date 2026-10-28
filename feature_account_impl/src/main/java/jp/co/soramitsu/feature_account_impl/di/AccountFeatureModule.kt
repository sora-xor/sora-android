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

package jp.co.soramitsu.feature_account_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_impl.data.repository.UserRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.UserRepositorySr25519Crypto
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsUserDatasource
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.SelectedAccountSigningSource

@Module
@InstallIn(SingletonComponent::class)
class AccountFeatureModule {

    @Provides
    @Singleton
    fun provideUserRepository(
        ud: UserDatasource,
        db: AppDatabase,
        cd: CredentialsDatasource,
        cm: CoroutineManager,
        lh: LanguagesHolder,
        sora2AddressCodec: Sora2AddressCodec,
        userRepositorySr25519Crypto: UserRepositorySr25519Crypto,
    ): UserRepository =
        UserRepositoryImpl(
            ud,
            cd,
            db,
            cm,
            lh,
            sora2AddressCodec,
            userRepositorySr25519Crypto,
        )

    /** The selected account's ability to sign, for every screen host (`SoraBaseFragment`). */
    @Provides
    @Singleton
    fun provideSelectedAccountSigningSource(
        userRepository: UserRepository,
    ): SelectedAccountSigningSource =
        SelectedAccountSigningSource { userRepository.flowSelectedAccountSigning() }

    @Provides
    @Singleton
    fun provideUserDatasource(
        sp: SoraPreferences,
        ep: EncryptedPreferences
    ): UserDatasource =
        PrefsUserDatasource(sp, ep)
}
