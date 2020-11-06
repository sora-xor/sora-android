/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network

import io.reactivex.Single
import jp.co.soramitsu.common.data.network.response.BaseResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.request.GetBalanceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.IrohaRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.response.GetBalanceResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.GetTransactionHistoryResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.GetTransferMetaResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.GetWithdrawalMetaResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.UserFindResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface WalletNetworkApi {

    @GET("/wallet/v1/user/contacts")
    fun getContacts(): Single<UserFindResponse>

    @POST("/wallet/v1/balance")
    fun getBalance(@Body request: GetBalanceRequest): Single<GetBalanceResponse>

    @POST("/wallet/v3/transaction")
    fun getTransactions(@Query("offset") offset: Int, @Query("count") count: Int): Single<GetTransactionHistoryResponse>

    @POST("/wallet/v1/transaction")
    fun transferVal(@Body request: IrohaRequest): Single<BaseResponse>

    @GET("/wallet/v1/user/find")
    fun findUser(@Query("search") search: String): Single<UserFindResponse>

    @GET("/wallet/v1/eth/withdraw/metadata")
    fun getWithdrawalMeta(@Query("assetId") assetId: String, @Query("option") option: String): Single<GetWithdrawalMetaResponse>

    @GET("/wallet/v1/transfer/metadata")
    fun getTransferMeta(@Query("assetId") assetId: String): Single<GetTransferMetaResponse>
}