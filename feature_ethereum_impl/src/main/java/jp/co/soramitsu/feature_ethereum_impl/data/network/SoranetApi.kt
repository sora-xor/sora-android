/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.network

import io.reactivex.Single
import jp.co.soramitsu.feature_ethereum_impl.data.network.response.WithdrawalProofsResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface SoranetApi {

    @GET("/v1/notification/find/withdrawalProofs/{accountId}")
    fun getWithdrawalProofs(@Path("accountId") accountId: String): Single<WithdrawalProofsResponse>
}