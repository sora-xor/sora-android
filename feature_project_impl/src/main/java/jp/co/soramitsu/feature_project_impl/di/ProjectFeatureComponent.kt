/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.di

import dagger.Component
import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.core_network_api.di.NetworkApi
import jp.co.soramitsu.feature_project_api.di.ProjectFeatureApi
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [
        ProjectFeatureDependencies::class
    ],
    modules = [
        ProjectFeatureModule::class
    ]
)
abstract class ProjectFeatureComponent : ProjectFeatureApi {

    @Component(
        dependencies = [
            CommonApi::class,
            NetworkApi::class,
            DbApi::class
        ]
    )
    interface ProjectFeatureDependenciesComponent : ProjectFeatureDependencies
}