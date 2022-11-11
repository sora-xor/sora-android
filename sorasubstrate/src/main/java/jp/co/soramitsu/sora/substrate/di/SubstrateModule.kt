/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.di

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.ConstantReconnectStrategy
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApi
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApiImpl
import jp.co.soramitsu.sora.substrate.substrate.WsConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.WsLogger
import javax.inject.Singleton

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
    fun provideReconnector(): Reconnector = Reconnector(strategy = ConstantReconnectStrategy(step = 10000L))

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
    ): ConnectionManager = WsConnectionManager(socketService, appStateProvider, coroutineManager)

    @Provides
    @Singleton
    fun provideSubstrateApi(api: SubstrateApiImpl): SubstrateApi = api
}
