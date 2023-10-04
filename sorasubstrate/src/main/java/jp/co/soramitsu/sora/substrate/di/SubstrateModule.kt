/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApi
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApiImpl
import jp.co.soramitsu.sora.substrate.substrate.WsConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.WsLogger
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigBuilder
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWallet
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWalletFactory
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

    @Singleton
    @Provides
    fun provideSubQueryClient(
        client: SoramitsuNetworkClient,
        factory: SubQueryClientForSoraWalletFactory,
        soraRemoteConfigBuilder: SoraRemoteConfigBuilder,
    ): SubQueryClientForSoraWallet = factory.create(
        soramitsuNetworkClient = client,
        pageSize = Const.HISTORY_PAGE_SIZE,
        soraRemoteConfigBuilder = soraRemoteConfigBuilder,
    )

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
}
