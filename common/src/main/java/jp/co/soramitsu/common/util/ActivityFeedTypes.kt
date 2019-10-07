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
        R.string.user_registered_type_template,
        R.string.user_registered_title_template, R.string.user_registered_description_template,
        R.drawable.icon_activity_invite
    ),

    USER_CREDITED_INVITATION(
        "UserCreditedInvitation",
        R.string.invitation_credited_type_template,
        R.string.invitation_credited_title_template,
        -1,
        R.drawable.icon_activity_invite
    ),

    USER_VOTED_FOR_PROJECT(
        "UserVotedForProject",
        R.string.voted_friend_added_type_template,
        R.string.voted_friend_added_title_template,
        R.string.voted_friend_added_description_template,
        R.drawable.icon_activity_vote
    ),

    VOTING_RIGHTS_CREDITED(
        "VotingRightsCredited",
        R.string.voting_rights_credited_type_template,
        R.string.voting_rights_title_template,
        -1,
        R.drawable.icon_activity_vote
    ),

    PROJECT_FUNDED(
        "ProjectFunded",
        R.string.project_funded_type_template,
        R.string.project_funded_title_template,
        R.string.project_funded_description_template,
        R.drawable.icon_activity_project
    ),

    PROJECT_CREATED(
        "ProjectCreated",
        R.string.project_created_type_template,
        R.string.project_created_title_template,
        R.string.project_created_description_template,
        R.drawable.icon_activity_project
    ),

    PROJECT_CLOSED(
        "ProjectClosed",
        R.string.project_closed_type_template,
        R.string.project_closed_title_template,
        -1,
        R.drawable.icon_activity_project
    ),

    XOR_REWARD_CREDITED_FROM_PROJECT(
        "XORRewardCreditedFromProject",
        R.string.xor_reward_credited_from_project_type_template,
        R.string.xor_reward_credited_from_project_title_template,
        R.string.xor_reward_credited_from_project_description_template,
        R.drawable.icon_activity_xor
    ),

    USER_RANK_CHANGED(
        "UserRankChanged",
        R.string.user_rank_changed_type_template,
        R.string.user_rank_changed_title_template,
        -1,
        R.drawable.icon_activity_reputation
    ),

    USER_REPUTATION_CHANGED(
        "UserReputationChanged",
        R.string.user_reputation_changed_type_template,
        R.string.user_reputation_changed_title_template,
        -1,
        R.drawable.icon_activity_reputation
    ),

    XOR_BETWEEN_USERS_TRANSFERRED(
        "XORBetweenUsersTransferred",
        R.string.xor_between_users_transferred_type_template,
        R.string.xor_between_users_transferred_title_template,
        R.string.xor_between_users_transferred_description_template,
        R.drawable.icon_activity_xor
    )
}