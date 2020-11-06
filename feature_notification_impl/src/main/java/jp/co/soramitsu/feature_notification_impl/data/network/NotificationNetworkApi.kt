/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_notification_impl.data.network

import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.response.BaseResponse
import jp.co.soramitsu.feature_notification_impl.data.network.request.PushRegistrationRequest
import jp.co.soramitsu.feature_notification_impl.data.network.request.TokenChangeRequest
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Streaming

interface NotificationNetworkApi {

    @POST("/notification/v1/register")
    fun registerToken(@Body pushRegistrationRequest: PushRegistrationRequest): Single<BaseResponse>

    @PUT("/notification/v1/tokens/change")
    fun changeToken(@Body tokenChangeRequest: TokenChangeRequest): Single<BaseResponse>

    @PUT("/notification/v1/permissions")
    fun setPermissions(@Body projectDids: Array<String>): Single<BaseResponse>

    @Streaming
    fun observeEvents(): Observable<ResponseBody>
}