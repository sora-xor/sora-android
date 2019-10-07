/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_impl

import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.core_network_impl.data.SoraCallAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class NetworkApiCreatorImpl(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String,
    private val rxCallAdapter: SoraCallAdapterFactory
) : NetworkApiCreator {

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