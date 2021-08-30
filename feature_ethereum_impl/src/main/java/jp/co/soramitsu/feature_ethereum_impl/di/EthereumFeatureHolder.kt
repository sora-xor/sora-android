package jp.co.soramitsu.feature_ethereum_impl.di

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.core_db.di.DbApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EthereumFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val ethereumFeatureDependencies = DaggerEthereumFeatureComponent_EthereumFeatureDependenciesComponent.builder()
            .networkApi(networkApi())
            .commonApi(commonApi())
            .dbApi(getFeature(DbApi::class.java))
            .build()
        return DaggerEthereumFeatureComponent.builder()
            .ethereumFeatureDependencies(ethereumFeatureDependencies)
            .build()
    }
}
