/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.mappers

import android.content.Context
import com.google.gson.JsonObject
import jp.co.soramitsu.common.util.ActivityFeedTypes
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_db.model.ActivityFeedLocal
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import jp.co.soramitsu.feature_account_impl.R
import java.util.Date
import javax.inject.Inject

fun mapActivityFeedLocalToActivityFeed(activityFeedLocal: ActivityFeedLocal): ActivityFeed {
    return with(activityFeedLocal) {
        ActivityFeed(type, title, description, votesString, Date(issuedAtMillis), iconDrawable)
    }
}

fun mapActivityFeedToActivityFeedLocal(activityFeed: ActivityFeed): ActivityFeedLocal {
    return with(activityFeed) {
        ActivityFeedLocal(0, type, title, description, votesString, issuedAt.time, iconDrawable)
    }
}

class ActivityGsonConverter @Inject constructor(
    private val context: Context,
    private val numbersFormatter: NumbersFormatter
) {
    fun convertActivityItems(activities: List<JsonObject>, projectDict: JsonObject, userDict: JsonObject, userDid: String): List<ActivityFeed> {
        return mutableListOf<ActivityFeed>().apply {
            activities.forEach {
                val activityVm = fromGsonToVm(it, projectDict, userDict, userDid)
                if (activityVm.type.isNotEmpty()) add(activityVm)
            }
        }
    }

    private fun fromGsonToVm(jsonActivity: JsonObject, projectDict: JsonObject, userDict: JsonObject, userDid: String): ActivityFeed {
        val projectId = jsonActivity.getAsJsonPrimitive("projectId")?.asString
        val projectData = projectDict.getAsJsonObject(projectId)
        val projectName = projectData?.getAsJsonPrimitive("projectName")?.asString
            ?: context.getString(R.string.project)

        var firstName = context.getString(R.string.user)
        var lastName = ""

        when (jsonActivity.getAsJsonPrimitive("type")?.asString) {
            ActivityFeedTypes.FRIEND_REGISTERED.typeCode -> {
                val userData = userDict.getAsJsonObject(jsonActivity.getAsJsonPrimitive("userId").asString)
                firstName = userData?.getAsJsonPrimitive("firstName")?.asString ?: firstName
                lastName = userData?.getAsJsonPrimitive("lastName")?.asString ?: lastName

                return ActivityFeed(
                    context.getString(ActivityFeedTypes.FRIEND_REGISTERED.typeStringResource),
                    context.getString(ActivityFeedTypes.FRIEND_REGISTERED.titleStringResource, firstName, lastName),
                    context.getString(ActivityFeedTypes.FRIEND_REGISTERED.descriptionStringResource),
                    "",
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.FRIEND_REGISTERED.iconDrawable
                )
            }
            ActivityFeedTypes.XOR_BETWEEN_USERS_TRANSFERRED.typeCode -> {
                val userData = if (jsonActivity.getAsJsonPrimitive("receiver").asString == userDid) userDict.getAsJsonObject(jsonActivity.getAsJsonPrimitive("sender").asString) else userDict.getAsJsonObject(jsonActivity.getAsJsonPrimitive("receiver").asString)
                firstName = userData?.getAsJsonPrimitive("firstName")?.asString ?: firstName
                lastName = userData?.getAsJsonPrimitive("lastName")?.asString ?: lastName

                return ActivityFeed(context.getString(ActivityFeedTypes.XOR_BETWEEN_USERS_TRANSFERRED.typeStringResource,
                    numbersFormatter.formatBigDecimal(jsonActivity.getAsJsonPrimitive("amount").asBigDecimal)),
                    context.getString(ActivityFeedTypes.XOR_BETWEEN_USERS_TRANSFERRED.titleStringResource, firstName, lastName),
                    context.getString(
                        ActivityFeedTypes.XOR_BETWEEN_USERS_TRANSFERRED.descriptionStringResource,
                        jsonActivity.getAsJsonPrimitive("message").asString
                    ),
                    "",
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.XOR_BETWEEN_USERS_TRANSFERRED.iconDrawable
                )
            }
            ActivityFeedTypes.VOTING_RIGHTS_CREDITED.typeCode ->
                return ActivityFeed(
                    context.getString(ActivityFeedTypes.VOTING_RIGHTS_CREDITED.typeStringResource),
                    context.getString(ActivityFeedTypes.VOTING_RIGHTS_CREDITED.titleStringResource),
                    "",
                    numbersFormatter.formatInteger(jsonActivity.getAsJsonPrimitive("votingRights").asBigDecimal),
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.VOTING_RIGHTS_CREDITED.iconDrawable
                )
            ActivityFeedTypes.USER_REPUTATION_CHANGED.typeCode ->
                return ActivityFeed(context.getString(ActivityFeedTypes.USER_REPUTATION_CHANGED.typeStringResource),
                    context.getString(
                        ActivityFeedTypes.USER_REPUTATION_CHANGED.titleStringResource,
                        jsonActivity.getAsJsonPrimitive("reputation").asBigDecimal.toString()
                    ),
                    "",
                    "",
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.USER_REPUTATION_CHANGED.iconDrawable
                )

            ActivityFeedTypes.USER_CREDITED_INVITATION.typeCode ->
                return ActivityFeed(context.getString(ActivityFeedTypes.USER_CREDITED_INVITATION.typeStringResource),
                    context.getString(
                        ActivityFeedTypes.USER_CREDITED_INVITATION.titleStringResource,
                        jsonActivity.getAsJsonPrimitive("invitations").asString
                    ),
                    "",
                    "",
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.USER_CREDITED_INVITATION.iconDrawable
                )
            ActivityFeedTypes.PROJECT_FUNDED.typeCode -> {
                return ActivityFeed(context.getString(ActivityFeedTypes.PROJECT_FUNDED.typeStringResource),
                    context.getString(ActivityFeedTypes.PROJECT_FUNDED.titleStringResource, projectName),
                    context.getString(ActivityFeedTypes.PROJECT_FUNDED.descriptionStringResource),
                    "",
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.PROJECT_FUNDED.iconDrawable
                )
            }
            ActivityFeedTypes.PROJECT_CREATED.typeCode ->
                return ActivityFeed(context.getString(ActivityFeedTypes.PROJECT_CREATED.typeStringResource),
                    context.getString(ActivityFeedTypes.PROJECT_CREATED.titleStringResource, projectName),
                    context.getString(
                        ActivityFeedTypes.PROJECT_CREATED.descriptionStringResource,
                        jsonActivity.getAsJsonPrimitive("description").asString
                    ),
                    "",
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.PROJECT_CREATED.iconDrawable
                )
            ActivityFeedTypes.PROJECT_CLOSED.typeCode ->
                return ActivityFeed(context.getString(ActivityFeedTypes.PROJECT_CLOSED.typeStringResource),
                    context.getString(ActivityFeedTypes.PROJECT_CLOSED.titleStringResource, projectName),
                    "",
                    "",
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.PROJECT_CLOSED.iconDrawable
                )
            ActivityFeedTypes.XOR_REWARD_CREDITED_FROM_PROJECT.typeCode ->
                return ActivityFeed(context.getString(ActivityFeedTypes.XOR_REWARD_CREDITED_FROM_PROJECT.typeStringResource),
                    context.getString(ActivityFeedTypes.XOR_REWARD_CREDITED_FROM_PROJECT.titleStringResource, projectName),
                    context.getString(
                        ActivityFeedTypes.XOR_REWARD_CREDITED_FROM_PROJECT.descriptionStringResource,
                        numbersFormatter.formatBigDecimal(jsonActivity.getAsJsonPrimitive("reward").asBigDecimal)
                    ),
                    "",
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.XOR_REWARD_CREDITED_FROM_PROJECT.iconDrawable
                )
            ActivityFeedTypes.USER_RANK_CHANGED.typeCode ->
                return ActivityFeed(context.getString(ActivityFeedTypes.USER_RANK_CHANGED.typeStringResource),
                    context.getString(
                        ActivityFeedTypes.USER_RANK_CHANGED.titleStringResource,
                        jsonActivity.getAsJsonPrimitive("rank").asString,
                        jsonActivity.getAsJsonPrimitive("totalRank").asString
                    ),
                    "",
                    "",
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.USER_RANK_CHANGED.iconDrawable
                )
            ActivityFeedTypes.USER_VOTED_FOR_PROJECT.typeCode -> {
                val userData = userDict.getAsJsonObject(jsonActivity.getAsJsonPrimitive("userId").asString)
                firstName = userData?.getAsJsonPrimitive("firstName")?.asString ?: firstName
                lastName = userData?.getAsJsonPrimitive("lastName")?.asString ?: lastName

                return ActivityFeed(context.getString(ActivityFeedTypes.USER_VOTED_FOR_PROJECT.typeStringResource),
                    context.getString(ActivityFeedTypes.USER_VOTED_FOR_PROJECT.titleStringResource, firstName, lastName),
                    context.getString(
                        ActivityFeedTypes.USER_VOTED_FOR_PROJECT.descriptionStringResource,
                        firstName,
                        numbersFormatter.formatInteger(jsonActivity.getAsJsonPrimitive("givenVotes").asBigDecimal),
                        projectName
                    ),
                    "",
                    Date(jsonActivity.getAsJsonPrimitive("issuedAt")?.asString!!.toLong() * 1000L),
                    ActivityFeedTypes.USER_VOTED_FOR_PROJECT.iconDrawable
                )
            }
        }

        return ActivityFeed("", "", "", "", Date(), -1)
    }
}