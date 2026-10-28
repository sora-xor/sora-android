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

package jp.co.soramitsu.sora.substrate.di

import android.content.Context
import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.sora.substrate.runtime.Sora2RecoveryRuntimeRetention
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.FileSora2SubmissionDetailsStore
import jp.co.soramitsu.sora.substrate.substrate.Sora2PendingSubmissionRuntimeRetention
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionDetailsStore
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApi
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApiImpl
import jp.co.soramitsu.sora.substrate.substrate.WsConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.WsLogger
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.logging.Logger
import jp.co.soramitsu.xsubstrate.wsrpc.recovery.ConstantReconnectStrategy
import jp.co.soramitsu.xsubstrate.wsrpc.recovery.Reconnector
import jp.co.soramitsu.xsubstrate.wsrpc.request.RequestExecutor

@InstallIn(SingletonComponent::class)
@Module
class SubstrateModule {

    @Provides
    @Singleton
    fun provideSocketFactory(): WebSocketFactory = WebSocketFactory()

    @Provides
    @Singleton
    fun provideWsSocketLogger(): Logger = WsLogger()

    @Provides
    @Singleton
    fun provideReconnector(): Reconnector =
        Reconnector(strategy = ConstantReconnectStrategy(step = 10000L))

    @Provides
    @Singleton
    fun provideRequestExecutor(): RequestExecutor = RequestExecutor()

    @Provides
    @Singleton
    fun provideSocketService(
        mapper: Gson,
        logger: Logger,
        socketFactory: WebSocketFactory,
        reconnector: Reconnector,
        requestExecutor: RequestExecutor,
    ): SocketService = SocketService(mapper, logger, socketFactory, reconnector, requestExecutor)

    @Provides
    @Singleton
    fun provideConnectionManager(
        socketService: SocketService,
        appStateProvider: AppStateProvider,
        coroutineManager: CoroutineManager,
        networkStateListener: NetworkStateListener,
        @ApplicationContext context: Context
    ): ConnectionManager = WsConnectionManager(
        socketService,
        appStateProvider,
        coroutineManager,
        networkStateListener,
        context
    )

    @Provides
    @Singleton
    fun provideSubstrateApi(api: SubstrateApiImpl): SubstrateApi = api

    /**
     * Non-secret Activity details of locally signed transactions. A plain file in the app's
     * private files directory: no database schema change, and nothing here is wallet key material.
     */
    @Provides
    @Singleton
    fun provideSora2SubmissionDetailsStore(
        @ApplicationContext context: Context,
    ): Sora2SubmissionDetailsStore = FileSora2SubmissionDetailsStore(
        file = java.io.File(context.filesDir, FileSora2SubmissionDetailsStore.FILE_NAME),
    )

    @Provides
    @Singleton
    fun provideSora2RecoveryRuntimeRetention(
        retention: Sora2PendingSubmissionRuntimeRetention,
    ): Sora2RecoveryRuntimeRetention = retention
}
