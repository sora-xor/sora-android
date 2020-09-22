/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.ProjectStatusConverter
import jp.co.soramitsu.core_db.converters.ProjectUrlConverter
import jp.co.soramitsu.core_db.converters.BigDecimalConverter
import java.math.BigDecimal
import java.net.URL

@Entity(tableName = "project_details")
@TypeConverters(ProjectStatusConverter::class, ProjectUrlConverter::class, BigDecimalConverter::class)
data class ProjectDetailsLocal(
    @PrimaryKey val id: String,
    val image: URL,
    val projectLink: URL,
    val name: String,
    val email: String,
    val description: String,
    val detailedDescription: String,
    val deadlineMillis: Long,
    val fundingCurrent: Double,
    val fundingTarget: Long,
    val votedFriendsCount: Int,
    val favoriteCount: Int,
    val votes: BigDecimal,
    val isFavorite: Boolean,
    val isUnwatched: Boolean,
    val status: ProjectStatusLocal,
    val statusUpdateTimeMillis: Long,
    @Embedded val discussionLink: DiscussionLinkLocal?
)