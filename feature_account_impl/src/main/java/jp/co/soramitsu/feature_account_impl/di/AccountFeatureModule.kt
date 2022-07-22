/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_impl.data.repository.UserRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsUserDatasource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AccountFeatureModule {

    @Provides
    @Singleton
    fun provideUserRepository(
        ud: UserDatasource,
        db: AppDatabase,
        dp: DeviceParamsProvider,
        cm: CoroutineManager,
    ): UserRepository =
        UserRepositoryImpl(ud, db, dp, cm)

    @Provides
    @Singleton
    fun provideUserDatasource(
        sp: SoraPreferences,
        ep: EncryptedPreferences
    ): UserDatasource =
        PrefsUserDatasource(sp, ep)
}
