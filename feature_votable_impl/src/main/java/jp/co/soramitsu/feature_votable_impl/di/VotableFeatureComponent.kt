package jp.co.soramitsu.feature_votable_impl.di

import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_votable_api.di.VotableFeatureApi
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [
        VotableFeatureDependencies::class
    ],
    modules = [
        VotableFeatureModule::class
    ]
)
abstract class VotableFeatureComponent : VotableFeatureApi {

    @Component(
        dependencies = [
            CommonApi::class,
            NetworkApi::class,
            DbApi::class
        ]
    )
    interface VotableFeatureDependenciesComponent : VotableFeatureDependencies
}