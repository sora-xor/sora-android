package jp.co.soramitsu.common.data.network

import jp.co.soramitsu.common.domain.AppLinksProvider
import okhttp3.OkHttpClient
import javax.inject.Named

interface NetworkApi {

    fun provideSora2CoroutineApiCreator(): Sora2CoroutineApiCreator

    fun appLinksProvider(): AppLinksProvider

    @Named("WEB3J_CLIENT") fun provideWeb3jOkHttpClient(): OkHttpClient.Builder
}
