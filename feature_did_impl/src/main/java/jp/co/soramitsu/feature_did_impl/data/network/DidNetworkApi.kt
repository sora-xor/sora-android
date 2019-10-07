/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_impl.data.network

import io.reactivex.Single
import jp.co.soramitsu.core_network_api.data.response.BaseResponse
import jp.co.soramitsu.feature_did_impl.data.network.response.GetDdoResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DidNetworkApi {

    @GET("/didresolver/v1/did/{did}")
    fun getDdo(@Path("did") did: String): Single<GetDdoResponse>

    @POST("/didresolver/v1/did")
    fun postDdo(@Body body: RequestBody): Single<BaseResponse>
}