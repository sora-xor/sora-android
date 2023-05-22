/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.feature_select_node_impl.NodeManagerImpl
import jp.co.soramitsu.feature_select_node_impl.data.SelectNodeRepositoryImpl
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
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
