/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import jp.co.soramitsu.common.R

enum class ActivityFeedTypes(
    val typeCode: String,
    val typeStringResource: Int,
    val titleStringResource: Int,
    val descriptionStringResource: Int,
    val iconDrawable: Int
) {

    FRIEND_REGISTERED(
        "FriendRegistered",
        R.string.activity_user_registered_type,
        R.string.activity_user_registered_title_template,
        R.string.activity_user_registered_description,
        R.drawable.icon_activity_invite
    ),

    USER_VOTED_FOR_PROJECT(
        "UserVotedForProject",
        R.string.activity_voted_friend_added_type_template,
        R.string.activity_voted_friend_added_title_template,
        R.string.activity_voted_friend_added_description_template,
        R.drawable.icon_activity_vote
    ),

    VOTING_RIGHTS_CREDITED(
        "VotingRightsCredited",
        R.string.activity_voting_rights_credited_type_template,
        R.string.activity_voting_rights_title_template,
        -1,
        R.drawable.icon_activity_vote
    ),

    XOR_REWARD_CREDITED_FROM_PROJECT(
        "XORRewardCreditedFromProject",
        R.string.activity_val_reward_credited_from_project_type_template,
        -1,
        -1,
        R.drawable.icon_activity_xor
    ),

    PROJECT_FUNDED(
        "ProjectFunded",
        R.string.activity_project_funded_type_template,
        R.string.activity_project_funded_title_template,
        R.string.activity_project_funded_description_template_val,
        R.drawable.icon_activity_project
    ),

    PROJECT_CREATED(
        "ProjectCreated",
        R.string.activity_project_created_type_template,
        R.string.activity_project_created_title_template,
        R.string.activity_project_created_description_template,
        R.drawable.icon_activity_project
    ),

    PROJECT_CLOSED(
        "ProjectClosed",
        R.string.activity_project_closed_type_template,
        R.string.activity_project_closed_title_template,
        -1,
        R.drawable.icon_activity_project
    ),

    USER_RANK_CHANGED(
        "UserRankChanged",
        R.string.activity_user_rank_changed_type_template,
        R.string.activity_user_rank_changed_title_template,
        -1,
        R.drawable.icon_activity_reputation
    ),

    XOR_BETWEEN_USERS_TRANSFERRED(
        "XORBetweenUsersTransferred",
        R.string.activity_val_between_users_transferred_type_template,
        -1,
        -1,
        R.drawable.icon_activity_xor
    )
}