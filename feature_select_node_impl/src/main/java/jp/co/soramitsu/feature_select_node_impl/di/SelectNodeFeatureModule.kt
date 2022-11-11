/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.feature_select_node_impl.NodeManagerImpl
import jp.co.soramitsu.feature_select_node_impl.data.SelectNodeRepositoryImpl
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.sorawallet.envbuilder.SoraEnvBuilder
import kotlinx.coroutines.FlowPreview
import javax.inject.Singleton

@FlowPreview
@Module
@InstallIn(SingletonComponent::class)
class SelectNodeFeatureModule {

    @Provides
    @Singleton
    fun provideSoraEnvBuild(
        soramitsuNetworkClient: SoramitsuNetworkClient
    ): SoraEnvBuilder = SoraEnvBuilder(
        soramitsuNetworkClient,
        baseUrl = SubstrateOptionsProvider.configFilePath
    )

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
    ): NodeManager = NodeManagerImpl(
        connectionManager = connectionManager,
        selectNodeRepository = selectNodeRepository,
        coroutineManager = coroutineManager,
        autoSwitch = true,
        appDatabase = appDatabase
    )
}
