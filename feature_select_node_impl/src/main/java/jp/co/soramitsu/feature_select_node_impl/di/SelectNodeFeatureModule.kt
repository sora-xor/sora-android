/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_select_node_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.feature_select_node_impl.NodeManagerImpl
import jp.co.soramitsu.feature_select_node_impl.data.SelectNodeRepositoryImpl
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.FlowPreview

@FlowPreview
@Module
@InstallIn(SingletonComponent::class)
class SelectNodeFeatureModule {

    @Provides
    @Singleton
    internal fun provideSelectNodeRepository(impl: SelectNodeRepositoryImpl): SelectNodeRepository = impl

    @Provides
    @Singleton
    internal fun provideNodeManager(
        connectionManager: ConnectionManager,
        selectNodeRepository: SelectNodeRepository,
        coroutineManager: CoroutineManager,
        appDatabase: AppDatabase,
        soraConfigManager: SoraConfigManager,
    ): NodeManager = NodeManagerImpl(
        connectionManager = connectionManager,
        selectNodeRepository = selectNodeRepository,
        coroutineManager = coroutineManager,
        autoSwitch = true,
        appDatabase = appDatabase,
        soraConfigManager = soraConfigManager,
    )
}
