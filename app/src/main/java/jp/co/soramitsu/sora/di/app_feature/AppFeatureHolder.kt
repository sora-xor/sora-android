package jp.co.soramitsu.sora.di.app_feature

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_notification_api.di.NotificationFeatureApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val appFeatureDependencies = DaggerAppFeatureComponent_AppFeatureDependenciesComponent.builder()
            .accountFeatureApi(getFeature(AccountFeatureApi::class.java))
            .notificationFeatureApi(getFeature(NotificationFeatureApi::class.java))
            .didFeatureApi(didApi())
            .commonApi(commonApi())
            .build()

        return DaggerAppFeatureComponent.builder()
            .appFeatureDependencies(appFeatureDependencies)
            .build()
    }
}