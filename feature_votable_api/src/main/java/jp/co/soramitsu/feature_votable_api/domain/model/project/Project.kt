/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_api.domain.model.project

import jp.co.soramitsu.feature_votable_api.domain.model.Votable
import java.math.BigDecimal
import java.net.URL
import java.util.Date
import java.util.concurrent.TimeUnit

data class Project(
    override val id: String,
    val image: URL,
    val projectLink: URL,
    var name: String,
    val email: String,
    val description: String,
    val detailedDescription: String,
    override val deadline: Date,
    val fundingCurrent: Double,
    val fundingTarget: Long,
    val votedFriendsCount: Int,
    val favoriteCount: Int,
    val votes: BigDecimal,
    var isFavorite: Boolean,
    val isUnwatched: Boolean,
    val status: ProjectStatus,
    override val statusUpdateTime: Date
) : Votable {
    override fun isSameAs(another: Votable) = another is Project && another == this

    fun getFundingPercent(): Int {
        return (fundingCurrent / fundingTarget * 100).toInt()
    }

    fun getVotesLeft(): Int {
        return (fundingTarget - fundingCurrent).toInt()
    }

    fun getLeftDays(): Long {
        return TimeUnit.DAYS.convert(this.deadline.time - Date().time, TimeUnit.MILLISECONDS)
    }
}