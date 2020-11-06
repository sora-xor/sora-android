/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.network

import io.reactivex.Single
import jp.co.soramitsu.feature_account_impl.data.network.response.AnnouncementResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.GetActivityFeedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ActivityFeedNetworkApi {

    @GET("/activityfeed/v1/feed/by_offset")
    fun getActivityFeed(@Query("count") count: Int, @Query("offset") offset: Int): Single<GetActivityFeedResponse>

    @GET("/information/v1/announcements")
    fun getAnnouncements(@Query("count") count: Int, @Query("offset") offset: Int): Single<AnnouncementResponse>
}