package jp.co.soramitsu.feature_votable_impl.di

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.core_db.di.DbApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VotableFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val votableFeatureDependencies = DaggerVotableFeatureComponent_VotableFeatureDependenciesComponent.builder()
            .networkApi(networkApi())
            .dbApi(getFeature(DbApi::class.java))
            .commonApi(commonApi())
            .build()
        return DaggerVotableFeatureComponent.builder()
            .votableFeatureDependencies(votableFeatureDependencies)
            .build()
    }
}
