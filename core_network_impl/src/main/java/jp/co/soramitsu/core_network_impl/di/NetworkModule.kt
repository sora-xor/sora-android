/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.core_network_api.data.auth.AuthHolder
import jp.co.soramitsu.core_network_api.domain.model.AppLinksProvider
import jp.co.soramitsu.core_network_impl.BuildConfig
import jp.co.soramitsu.core_network_impl.NetworkApiCreatorImpl
import jp.co.soramitsu.core_network_impl.data.SoraCallAdapterFactory
import jp.co.soramitsu.core_network_impl.data.auth.AuthHolderImpl
import jp.co.soramitsu.core_network_impl.data.auth.DAuthRequestInterceptor
import jp.co.soramitsu.core_network_impl.util.HttpLoggingInterceptor
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
    fun provideAuthHolder(authHolder: AuthHolderImpl): AuthHolder = authHolder

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
    fun provideSoraOkhttpClient(
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
    fun provideApiCreator(
        @Named("SORA_CLIENT") okHttpClient: OkHttpClient,
        rxCallAdapter: SoraCallAdapterFactory
    ): NetworkApiCreator {
        return NetworkApiCreatorImpl(okHttpClient, BuildConfig.HOST_URL, rxCallAdapter)
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
    fun provideAppLinksProvider(
        @Named("DEFAULT_MARKET_URL") defaultMarketUrl: String,
        @Named("INVITE_LINK_URL") invitekUrl: String
    ): AppLinksProvider {
        return AppLinksProvider(defaultMarketUrl, invitekUrl)
    }
}