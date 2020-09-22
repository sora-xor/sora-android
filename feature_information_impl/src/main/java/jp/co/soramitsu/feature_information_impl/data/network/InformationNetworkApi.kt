package jp.co.soramitsu.feature_information_impl.data.network

import io.reactivex.Single
import jp.co.soramitsu.feature_information_impl.data.network.response.GetInformationResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface InformationNetworkApi {

    @GET("/information/v1/information/{sectionName}")
    fun getInformation(@Path("sectionName") sectionName: String): Single<GetInformationResponse>
}