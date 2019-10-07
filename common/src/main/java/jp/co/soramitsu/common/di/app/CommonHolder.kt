/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.app

import android.content.Context
import jp.co.soramitsu.core_di.holder.FeatureApiHolder
import jp.co.soramitsu.core_di.holder.FeatureContainer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommonHolder @Inject constructor(
    featureContainer: FeatureContainer,
    private val mApplicationContext: Context
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        return DaggerCommonComponent.builder()
            .context(mApplicationContext)
            .build()
    }
}