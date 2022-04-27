/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.sorascan

import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.feature_wallet_impl.data.network.request.SubqueryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.response.HistoryResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.PoolsInfoResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface SoraScanApi {

    @POST("//${OptionsProvider.soraScanUrl}")
    suspend fun getHistory(@Body request: SubqueryRequest): HistoryResponse

    @POST("//${OptionsProvider.soraScanUrl}")
    suspend fun getStrategicBonusAPY(@Body request: SubqueryRequest): PoolsInfoResponse
}
