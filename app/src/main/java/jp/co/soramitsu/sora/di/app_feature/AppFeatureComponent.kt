/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app_feature

import dagger.Component
import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_did_api.di.DidFeatureApi
import jp.co.soramitsu.sora.service.PushNotificationService
import jp.co.soramitsu.sora.splash.di.SplashComponent
import javax.inject.Singleton

@Component(
    dependencies = [
        AppFeatureDependencies::class
    ]
)
@Singleton
interface AppFeatureComponent {

    @Component(
        dependencies = [
            AccountFeatureApi::class,
            DidFeatureApi::class,
            CommonApi::class
        ]
    )
    interface AppFeatureDependenciesComponent : AppFeatureDependencies

    fun inject(service: PushNotificationService)

    fun splashComponentBuilder(): SplashComponent.Builder
}