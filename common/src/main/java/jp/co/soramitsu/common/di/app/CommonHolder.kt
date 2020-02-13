/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.app

import android.content.Context
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_di.holder.FeatureApiHolder
import jp.co.soramitsu.core_di.holder.FeatureContainer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommonHolder @Inject constructor(
    featureContainer: FeatureContainer,
    private val resourceManager: ResourceManager,
    private val context: Context,
    private val contextManager: ContextManager
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        return DaggerCommonComponent.builder()
            .resourceManager(resourceManager)
            .context(context)
            .languagesHolder(contextManager.getLanguages())
            .contextManager(contextManager)
            .build()
    }
}