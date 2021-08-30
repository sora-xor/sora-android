/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app.modules

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.sora.di.app.FeatureHolderManager
import javax.inject.Singleton

@Module
class FeatureManagerModule {

    @Singleton
    @Provides
    fun provideFeatureHolderManager(featureApiHolderMap: @JvmSuppressWildcards Map<Class<*>, FeatureApiHolder>): FeatureHolderManager {
        return FeatureHolderManager(featureApiHolderMap)
    }
}
