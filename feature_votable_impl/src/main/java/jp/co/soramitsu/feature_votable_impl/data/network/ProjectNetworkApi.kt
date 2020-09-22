package jp.co.soramitsu.feature_votable_impl.data.network

import io.reactivex.Single
import jp.co.soramitsu.common.data.network.response.BaseResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetProjectDetailsResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetProjectResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetProjectVotesResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetVotesHistoryResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ProjectNetworkApi {

    @GET("/project/v1/project/user/votes")
    fun getVotes(): Single<GetProjectVotesResponse>

    @GET("/project/v1/project/opened")
    fun getAllProjects(): Single<GetProjectResponse>

    @GET("/project/v1/project/voted")
    fun getVotedProjects(): Single<GetProjectResponse>

    @GET("/project/v1/project/finished")
    fun getFinishedProjects(): Single<GetProjectResponse>

    @GET("/project/v1/project/favorite")
    fun getFavoriteProjects(): Single<GetProjectResponse>

    @GET("/project/v1/project/{projectId}/details")
    fun getProjectDetails(@Path("projectId") projectId: String): Single<GetProjectDetailsResponse>

    @PUT("/project/v1/project/{projectId}/favorite")
    fun toggleFavoriteProject(@Path("projectId") projectId: String): Single<BaseResponse>

    @Headers("Content-Type: application/json")
    @POST("/project/v1/project/{projectId}/vote")
    fun voteForProject(@Path("projectId") projectId: String, @Query("votes") votesNumber: Long): Single<BaseResponse>

    @GET("/project/v1/project/user/votes/history")
    fun getVotesHistory(@Query("count") count: Int, @Query("offset") skip: Int): Single<GetVotesHistoryResponse>
}