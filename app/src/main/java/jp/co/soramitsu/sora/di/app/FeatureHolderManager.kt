/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app

import jp.co.soramitsu.common.di.api.FeatureApiHolder

class FeatureHolderManager(
    private val mFeatureHolders: Map<Class<*>, FeatureApiHolder>
) {

    fun <T> getFeature(key: Class<*>): T? {
        val featureApiHolder = mFeatureHolders[key] ?: throw IllegalStateException()
        return featureApiHolder.getFeatureApi<T>()
    }

    fun releaseFeature(key: Class<*>) {
        val featureApiHolder = mFeatureHolders[key] ?: throw IllegalStateException()
        featureApiHolder.releaseFeatureApi()
    }
}
