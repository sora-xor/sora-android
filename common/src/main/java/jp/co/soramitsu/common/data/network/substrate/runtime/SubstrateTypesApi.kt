package jp.co.soramitsu.common.data.network.substrate.runtime

import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Path

interface SubstrateTypesApi {

    @GET("//raw.githubusercontent.com/polkascan/py-scale-codec/master/scalecodec/type_registry/default.json")
    fun getDefaultTypes(): Single<String>

    @GET("//raw.githubusercontent.com/sora-xor/sora2-types/master/{subPath}/sora2_types.json")
    fun getSora2Types(@Path("subPath") subPath: String): Single<String>
}
