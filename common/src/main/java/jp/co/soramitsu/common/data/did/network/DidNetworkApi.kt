/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.did.network

import io.reactivex.Single
import jp.co.soramitsu.common.data.did.network.response.GetDdoResponse
import jp.co.soramitsu.common.data.network.response.BaseResponse
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