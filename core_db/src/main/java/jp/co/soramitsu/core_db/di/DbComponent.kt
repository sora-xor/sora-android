/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.di

import dagger.Component
import jp.co.soramitsu.common.di.app.CommonApi
import javax.inject.Singleton

@Component(
    modules = [
        DbModule::class
    ],
    dependencies = [
        DbDependencies::class
    ]
)
@Singleton
abstract class DbComponent : DbApi {

    @Component(
        dependencies = [
            CommonApi::class
        ]
    )
    interface DbDependenciesComponent : DbDependencies
}