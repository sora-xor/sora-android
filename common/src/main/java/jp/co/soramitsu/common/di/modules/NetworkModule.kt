/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.modules

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.HttpLoggingInterceptor
import jp.co.soramitsu.common.data.network.Sora2CoroutineApiCreator
import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.data.network.substrate.SocketSingleRequestExecutor
import jp.co.soramitsu.common.data.network.substrate.WsConnectionManager
import jp.co.soramitsu.common.data.network.substrate.WsLogger
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import okhttp3.OkHttpClient
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
class NetworkModule {

    @Provides
    @Singleton
    fun provideSocketFactory(): WebSocketFactory = WebSocketFactory()

    @Provides
    @Singleton
    fun provideWsSocketLogger(): Logger = WsLogger()

    @Provides
    @Singleton
    fun provideReconnector(): Reconnector = Reconnector()

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
    fun provideSocketSingleRequestExecutor(
        mapper: Gson,
        socketFactory: WebSocketFactory,
        resourceManager: ResourceManager
    ): SocketSingleRequestExecutor =
        SocketSingleRequestExecutor(mapper, socketFactory, resourceManager)

    @Provides
    @Singleton
    fun provideSSLSocketFactory(sslContext: SSLContext): SSLSocketFactory {
        return sslContext.socketFactory
    }

    @Provides
    @Singleton
    fun provideTrustManager(): Array<TrustManager> {
        return arrayOf(object : X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(
                chain: Array<java.security.cert.X509Certificate>,
                authType: String
            ) {
            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(
                chain: Array<java.security.cert.X509Certificate>,
                authType: String
            ) {
            }

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                return arrayOf()
            }
        })
    }

    @Provides
    @Singleton
    fun provideSSLContext(trustManagers: Array<TrustManager>): SSLContext {
        val trustAllSslContext = SSLContext.getInstance("SSL")
        trustAllSslContext.init(null, trustManagers, java.security.SecureRandom())
        return trustAllSslContext
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        val logging = HttpLoggingInterceptor()
        if (BuildConfig.DEBUG) {
            logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        } else {
            logging.setLevel(HttpLoggingInterceptor.Level.NONE)
        }
        return logging
    }

    @Provides
    @Singleton
    @Named("SORA_CLIENT")
    fun provideSoraOkHttpClient(
        logging: HttpLoggingInterceptor,
        sslSocketFactory: SSLSocketFactory,
        trustManagers: Array<TrustManager>
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManagers[0] as X509TrustManager)
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @Named("SORA2_NET_CLIENT")
    fun provideSoraNetOkHttpClient(
        logging: HttpLoggingInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("WEB3J_CLIENT")
    fun provideWeb3jOkHttpClient(
        logging: HttpLoggingInterceptor
    ): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
    }

    @Provides
    @Singleton
    @Named("SORA_HOST_URL")
    fun provideSoraHostUrl(): String = BuildConfig.HOST_URL

    @Provides
    @Singleton
    fun provideSora2CoroutineApiCreator(@Named("SORA2_NET_CLIENT") okHttpClient: OkHttpClient): Sora2CoroutineApiCreator {
        return Sora2CoroutineApiCreator(okHttpClient, OptionsProvider.soraScanUrl)
    }

    @Singleton
    @Provides
    @Named("DEFAULT_MARKET_URL")
    fun provideMarketUrl(): String = BuildConfig.DEFAULT_MARKET_URL

    @Singleton
    @Provides
    @Named("INVITE_LINK_URL")
    fun provideInviteLinkUrl(): String = BuildConfig.INVITE_LINK_URL

    @Singleton
    @Provides
    fun provideAppLinksProvider(
        @Named("SORA_HOST_URL") soraHostUrl: String,
        @Named("DEFAULT_MARKET_URL") defaultMarketUrl: String,
        @Named("INVITE_LINK_URL") inviteUrl: String
    ): AppLinksProvider {
        return AppLinksProvider(soraHostUrl, defaultMarketUrl, inviteUrl, "")
    }
}
