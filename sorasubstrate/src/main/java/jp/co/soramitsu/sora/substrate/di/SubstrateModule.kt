/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.logging.Logger
import jp.co.soramitsu.shared_utils.wsrpc.recovery.ConstantReconnectStrategy
import jp.co.soramitsu.shared_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.shared_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApi
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApiImpl
import jp.co.soramitsu.sora.substrate.substrate.WsConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.WsLogger
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.SoraWalletBlockExplorerInfo
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigBuilder
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigProvider
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWallet
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWalletFactory

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
    fun provideSoraWalletBlockExplorerInfo(
        client: SoramitsuNetworkClient,
        soraRemoteConfigBuilder: SoraRemoteConfigBuilder,
    ): SoraWalletBlockExplorerInfo {
        return SoraWalletBlockExplorerInfo(
            networkClient = client,
            soraRemoteConfigBuilder = soraRemoteConfigBuilder,
        )
    }

    @Singleton
    @Provides
    fun provideSoraRemoteConfigBuilder(
        client: SoramitsuNetworkClient,
        @ApplicationContext context: Context,
    ): SoraRemoteConfigBuilder {
        return SoraRemoteConfigProvider(
            context = context,
            client = client,
            commonUrl = SubstrateOptionsProvider.configCommon,
            mobileUrl = SubstrateOptionsProvider.configMobile,
        ).provide()
    }

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
