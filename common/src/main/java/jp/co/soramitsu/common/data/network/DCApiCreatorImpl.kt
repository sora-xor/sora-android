package jp.co.soramitsu.common.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DCApiCreatorImpl(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String,
    private val rxCallAdapter: SoraCallAdapterFactory
) : DCApiCreator {

    override fun <T> create(service: Class<T>): T {
        val retrofit = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(baseUrl)
            .addCallAdapterFactory(rxCallAdapter)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(service)
    }
}