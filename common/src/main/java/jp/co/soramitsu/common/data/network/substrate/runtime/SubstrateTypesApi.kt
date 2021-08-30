/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.substrate.runtime

import retrofit2.http.GET
import retrofit2.http.Path

interface SubstrateTypesApi {

    @GET("//raw.githubusercontent.com/polkascan/py-scale-codec/master/scalecodec/type_registry/default.json")
    suspend fun getDefaultTypes(): String

    @GET("//raw.githubusercontent.com/sora-xor/sora2-types/master/{subPath}/sora2_types.json")
    suspend fun getSora2Types(@Path("subPath") subPath: String): String
}
