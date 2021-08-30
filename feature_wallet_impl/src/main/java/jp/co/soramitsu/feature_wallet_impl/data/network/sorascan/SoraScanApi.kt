/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.sorascan

import jp.co.soramitsu.feature_wallet_impl.data.network.response.ExtrinsicSwapResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.ExtrinsicsPageResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.TransactionsPageResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SoraScanApi {

    @GET("/api/v1/balances/transfer")
    suspend fun getTransactionsPaged(
        @Query("filter[address]") address: String,
        @Query("page[number]") pageNumber: Long,
        @Query("page[size]") pageSize: Int
    ): TransactionsPageResponse

    @GET("/api/v1/extrinsic?filter[signed]=1&filter[module_id]=Assets&filter[call_id]=transfer&filter[success]=0&filter[error]=1")
    suspend fun getAssetsTransfersErrorPaged(
        @Query("filter[address]") address: String,
        @Query("page[number]") pageNumber: Long,
        @Query("page[size]") pageSize: Int
    ): ExtrinsicsPageResponse

    @GET("/api/v1/extrinsic?filter[signed]=1&filter[module_id]=LiquidityProxy&filter[call_id]=swap")
    suspend fun getLiquiditySwapPaged(
        @Query("filter[address]") address: String,
        @Query("page[number]") pageNumber: Long,
        @Query("page[size]") pageSize: Int
    ): ExtrinsicsPageResponse

    @GET("/api/v1/extrinsic/0x{extrinsicId}")
    suspend fun getSwapExtrinsicDetails(
        @Path("extrinsicId") id: String,
    ): ExtrinsicSwapResponse
}
