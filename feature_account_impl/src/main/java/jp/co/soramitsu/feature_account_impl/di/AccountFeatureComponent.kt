package jp.co.soramitsu.feature_account_impl.di

import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [
        AccountFeatureDependencies::class
    ],
    modules = [
        AccountFeatureModule::class
    ]
)
abstract class AccountFeatureComponent : AccountFeatureApi {

    @Component(
        dependencies = [
            CommonApi::class,
            NetworkApi::class,
            DbApi::class
        ]
    )
    internal interface AccountFeatureDependenciesComponent : AccountFeatureDependencies
}
