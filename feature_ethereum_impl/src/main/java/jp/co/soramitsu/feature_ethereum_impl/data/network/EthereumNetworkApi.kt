package jp.co.soramitsu.feature_ethereum_impl.data.network

import io.reactivex.Single
import jp.co.soramitsu.common.data.network.response.BaseResponse
import jp.co.soramitsu.feature_ethereum_impl.data.network.request.IrohaRequest
import jp.co.soramitsu.feature_ethereum_impl.data.network.response.EthRegisterStateResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface EthereumNetworkApi {

    @POST("/wallet/v1/eth/register")
    fun ethRegister(@Body request: IrohaRequest): Single<BaseResponse>

    @POST("/wallet/v1/eth/withdraw")
    fun withdraw(@Body request: IrohaRequest): Single<BaseResponse>

    @GET("/wallet/v1/eth/register/state")
    fun getEthRegisterState(): Single<EthRegisterStateResponse>
}