/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_api.domain.model

import androidx.databinding.BaseObservable
import java.math.BigDecimal
import java.net.URL
import java.util.Date
import java.util.concurrent.TimeUnit

data class ProjectDetails(
    val id: String,
    val image: URL,
    val projectLink: URL,
    var name: String,
    val email: String,
    val description: String,
    val detailedDescription: String,
    val deadline: Date,
    val fundingCurrent: Long,
    val fundingTarget: Long,
    val votedFriendsCount: Int,
    val favoriteCount: Int,
    val votes: BigDecimal,
    var isFavorite: Boolean,
    val isUnwatched: Boolean,
    val gallery: List<GalleryItem>,
    val status: ProjectStatus,
    val statusUpdateTime: Date
) : BaseObservable() {

    fun getFundingPercent(): Int {
        return (fundingCurrent / fundingTarget.toDouble() * 100).toInt()
    }

    fun getLeftDays(): Long {
        return TimeUnit.DAYS.convert(this.deadline.time - Date().time, TimeUnit.MILLISECONDS)
    }
}