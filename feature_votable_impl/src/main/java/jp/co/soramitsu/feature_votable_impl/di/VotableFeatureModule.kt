/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import jp.co.soramitsu.feature_votable_impl.data.local.PrefsVotesDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class VotableFeatureModule {
    @Provides
    @Singleton
    fun provideProjectDataSource(sp: SoraPreferences): VotesDataSource = PrefsVotesDataSource(sp)
}
