/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.network

import io.reactivex.Single
import jp.co.soramitsu.core_network_api.data.response.BaseResponse
import jp.co.soramitsu.feature_account_impl.data.network.request.CreateUserRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.RegistrationRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.SaveUserDataRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.VerifyCodeRequest
import jp.co.soramitsu.feature_account_impl.data.network.response.CheckVersionSupportResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.GetCountriesResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.GetInvitationResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.GetReputationResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.GetUserResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.GetUserValuesResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.InvitedUsersResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.SendSMSResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AccountNetworkApi {

    @GET("/account/v1/invitation")
    fun getInvitationCode(): Single<GetInvitationResponse>

    @GET("/account/v1/user/invited")
    fun getInvitedUsers(): Single<InvitedUsersResponse>

    @GET("/account/v1/user")
    fun getUser(): Single<GetUserResponse>

    @GET("/account/v1/user/values")
    fun getUserValues(): Single<GetUserValuesResponse>

    @GET("/account/v1/user/reputation")
    fun getUserReputation(): Single<GetReputationResponse>

    @GET("/information/v1/information/country")
    fun getAllCountries(): Single<GetCountriesResponse>

    @PUT("/account/v1/invitation/is_sent/{invitationCode}")
    fun sendCodeIsSent(@Path("invitationCode") invitationCode: String): Single<BaseResponse>

    @POST("/account/v1/user/register")
    fun register(@Body registrationRequest: RegistrationRequest): Single<BaseResponse>

    @PUT("/account/v1/user")
    fun saveUserData(@Body saveUserDataRequest: SaveUserDataRequest): Single<BaseResponse>

    @Headers("Content-Type: application/json")
    @POST("/account/v1/smscode/send")
    fun requestSMSCode(): Single<SendSMSResponse>

    @POST("/account/v1/smscode/verify")
    fun verifySMSCode(@Body code: VerifyCodeRequest): Single<BaseResponse>

    @POST("/account/v1/user/create")
    fun createUser(@Body phoneNumber: CreateUserRequest): Single<SendSMSResponse>

    @GET("/information/v1/supported/android")
    fun checkVersionSupported(@Query("version") versionName: String): Single<CheckVersionSupportResponse>
}