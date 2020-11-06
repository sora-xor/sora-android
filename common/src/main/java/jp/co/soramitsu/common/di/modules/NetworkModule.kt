package jp.co.soramitsu.common.di.modules

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.DCApiCreator
import jp.co.soramitsu.common.data.network.DCApiCreatorImpl
import jp.co.soramitsu.common.data.network.HttpLoggingInterceptor
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.NetworkApiCreatorImpl
import jp.co.soramitsu.common.data.network.SoraCallAdapterFactory
import jp.co.soramitsu.common.data.network.SoranetApiCreator
import jp.co.soramitsu.common.data.network.SoranetApiCreatorImpl
import jp.co.soramitsu.common.data.network.auth.AuthHolder
import jp.co.soramitsu.common.data.network.auth.DAuthRequestInterceptor
import jp.co.soramitsu.common.data.network.sse.SseClient
import jp.co.soramitsu.common.data.network.sse.SseClientImpl
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.resourses.ResourceManager
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
    fun provideSSLSocketFactory(sslContext: SSLContext): SSLSocketFactory {
        return sslContext.socketFactory
    }

    @Provides
    @Singleton
    fun provideTrustManager(): Array<TrustManager> {
        return arrayOf(object : X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
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
    fun provideAuthHolder(): AuthHolder = AuthHolder()

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
        auth: DAuthRequestInterceptor,
        sslSocketFactory: SSLSocketFactory,
        trustManagers: Array<TrustManager>
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManagers[0] as X509TrustManager)
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addNetworkInterceptor(auth)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @Named("SORA_NET_CLIENT")
    fun provideSoraNetOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("SORA_SSE_CLIENT")
    fun provideSoraSseOkHttpClient(auth: DAuthRequestInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addNetworkInterceptor(auth)
            .build()
    }

    @Provides
    @Singleton
    @Named("DC_CLIENT")
    fun provideDCClient(
        logging: HttpLoggingInterceptor,
        auth: DAuthRequestInterceptor,
        sslSocketFactory: SSLSocketFactory,
        trustManagers: Array<TrustManager>
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManagers[0] as X509TrustManager)
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addNetworkInterceptor(auth)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideSseClient(
        @Named("SORA_SSE_CLIENT") okHttpClient: OkHttpClient,
        serializer: Serializer
    ): SseClient {
        return SseClientImpl(okHttpClient, serializer)
    }

    @Provides
    @Singleton
    @Named("SORA_HOST_URL")
    fun provideSoraHostUrl(): String = BuildConfig.HOST_URL

    @Provides
    @Singleton
    fun provideApiCreator(
        @Named("SORA_CLIENT") okHttpClient: OkHttpClient,
        rxCallAdapter: SoraCallAdapterFactory,
        @Named("SORA_HOST_URL") soraHostUrl: String
    ): NetworkApiCreator {
        return NetworkApiCreatorImpl(okHttpClient, soraHostUrl, rxCallAdapter)
    }

    @Provides
    @Singleton
    fun provideSoranetApiCreator(
        @Named("SORA_NET_CLIENT") okHttpClient: OkHttpClient,
        rxCallAdapter: SoraCallAdapterFactory
    ): SoranetApiCreator {
        return SoranetApiCreatorImpl(okHttpClient, BuildConfig.SORA_EVENTS_URL, rxCallAdapter)
    }

    @Provides
    @Singleton
    fun provideDCApiCreator(
        @Named("DC_CLIENT") okHttpClient: OkHttpClient,
        rxCallAdapter: SoraCallAdapterFactory
    ): DCApiCreator {
        return DCApiCreatorImpl(okHttpClient, BuildConfig.DC_HOST_URL, rxCallAdapter)
    }

    @Singleton
    @Provides
    fun provideSoraCallAdapter(healthChecker: HealthChecker, resourceManager: ResourceManager): SoraCallAdapterFactory {
        return SoraCallAdapterFactory(healthChecker, resourceManager)
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
    @Named("BLOCKCHAIN_EXPLORER_URL")
    fun provideBlockChainExplorerUrl(): String = BuildConfig.BLOCKCHAIN_EXPLORER_URL

    @Singleton
    @Provides
    @Named("ETHERSCAN_EXPLORER_URL")
    fun provideEtherscanChainExplorerUrl(): String = BuildConfig.ETHERSCAN_EXPLORER_URL

    @Singleton
    @Provides
    fun provideAppLinksProvider(
        @Named("SORA_HOST_URL") soraHostUrl: String,
        @Named("DEFAULT_MARKET_URL") defaultMarketUrl: String,
        @Named("INVITE_LINK_URL") invitekUrl: String,
        @Named("BLOCKCHAIN_EXPLORER_URL") blockChainExplorerUrl: String,
        @Named("ETHERSCAN_EXPLORER_URL") etherscanExplorerUrl: String
    ): AppLinksProvider {
        return AppLinksProvider(soraHostUrl, defaultMarketUrl, invitekUrl, blockChainExplorerUrl, etherscanExplorerUrl)
    }
}