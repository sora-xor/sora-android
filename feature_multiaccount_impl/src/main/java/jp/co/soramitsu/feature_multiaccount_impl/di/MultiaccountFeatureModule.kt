/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.core_di.holder.scope.FeatureScope
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.feature_multiaccount_impl.MultiaccountRouterImpl
import jp.co.soramitsu.feature_multiaccount_impl.MultiaccountStarterImpl
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter

@Module
class MultiaccountFeatureModule {

    @Provides
    fun provideMultiaccountStarter(router: MultiaccountRouter): MultiaccountStarter = MultiaccountStarterImpl(router)

    @FeatureScope
    @Provides
    fun provideMultiaccountRouter(): MultiaccountRouter = MultiaccountRouterImpl()
}
