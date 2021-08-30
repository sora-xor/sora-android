package jp.co.soramitsu.feature_ethereum_impl.util

import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.JsonRpc2_0Web3j
import org.web3j.protocol.http.HttpService
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class Web3jProvider @Inject constructor(
    @Named("WEB3J_CLIENT") okHttpClient: OkHttpClient.Builder
) {

    val web3j: Web3j by lazy {
        okHttpClient.addNetworkInterceptor {
            val credential: String = Credentials.basic("config.userName", "config.password")
            val newRequest = it.request().newBuilder().header("Authorization", credential).build()
            it.proceed(newRequest)
        }
        JsonRpc2_0Web3j((HttpService("config.url", okHttpClient.build())))
    }
}
