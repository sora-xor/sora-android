package jp.co.soramitsu.feature_votable_impl.data.network

import io.reactivex.Single
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetReferendumDetailsResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetReferendumsResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.VoteResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ReferendumNetworkApi {
    @GET("/project/v1/referendum/opened")
    fun getOpenedReferendums(): Single<GetReferendumsResponse>

    @GET("/project/v1/referendum/voted")
    fun getVotedReferendums(): Single<GetReferendumsResponse>

    @GET("/project/v1/referendum/finished")
    fun getFinishedReferendums(): Single<GetReferendumsResponse>

    @GET("/project/v1/referendum/{referendumId}/details")
    fun getReferendumDetails(@Path("referendumId") projectId: String): Single<GetReferendumDetailsResponse>

    @POST("/project/v1/referendum/{referendumId}/support")
    fun voteForReferendum(
        @Path("referendumId") projectId: String,
        @Query("votes") votesNumber: Long
    ): Single<VoteResponse>

    @POST("/project/v1/referendum/{referendumId}/oppose")
    fun voteAgainstReferendum(
        @Path("referendumId") projectId: String,
        @Query("votes") votesNumber: Long
    ): Single<VoteResponse>
}