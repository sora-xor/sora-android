/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.substrate.runtime

import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import retrofit2.http.GET

interface SubstrateTypesApi {

    @GET("//raw.githubusercontent.com/polkascan/py-scale-codec/master/scalecodec/type_registry/default.json")
    suspend fun getDefaultTypes(): String

    @GET("//raw.githubusercontent.com/sora-xor/sora2-substrate-js-library/master/packages/types/src/metadata/${OptionsProvider.typesFilePath}/types_scalecodec_mobile.json")
    suspend fun getSora2Types(): String
}
