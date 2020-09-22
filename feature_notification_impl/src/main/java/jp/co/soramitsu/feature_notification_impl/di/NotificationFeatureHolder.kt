/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_notification_impl.di

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val notificationFeatureDependencies = DaggerNotificationFeatureComponent_NotificationFeatureDependenciesComponent.builder()
            .networkApi(networkApi())
            .commonApi(commonApi())
            .build()
        return DaggerNotificationFeatureComponent.builder()
            .notificationFeatureDependencies(notificationFeatureDependencies)
            .build()
    }
}