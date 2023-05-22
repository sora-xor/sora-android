/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.feature_referral_impl.data.ReferralRepositoryImpl
import kotlinx.coroutines.FlowPreview

@FlowPreview
@Module
@InstallIn(SingletonComponent::class)
class ReferralFeatureModule {

    @Provides
    @Singleton
    fun provideReferralRepository(impl: ReferralRepositoryImpl): ReferralRepository = impl
}
