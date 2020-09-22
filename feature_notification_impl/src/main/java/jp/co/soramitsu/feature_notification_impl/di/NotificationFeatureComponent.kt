package jp.co.soramitsu.feature_notification_impl.di

import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.feature_notification_api.di.NotificationFeatureApi
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [
        NotificationFeatureDependencies::class
    ],
    modules = [
        NotificationFeatureModule::class
    ]
)
abstract class NotificationFeatureComponent : NotificationFeatureApi {

    @Component(
        dependencies = [
            CommonApi::class,
            NetworkApi::class
        ]
    )
    internal interface NotificationFeatureDependenciesComponent : NotificationFeatureDependencies
}