package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.core_db.model.ActivityFeedLocal
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import java.util.Date

fun mapActivityFeedLocalToActivityFeed(activityFeedLocal: ActivityFeedLocal): ActivityFeed {
    return with(activityFeedLocal) {
        ActivityFeed(type, title, description, votesString, Date(issuedAtMillis), iconDrawable, activityFeedLocal.votesRightDrawable)
    }
}

fun mapActivityFeedToActivityFeedLocal(activityFeed: ActivityFeed): ActivityFeedLocal {
    return with(activityFeed) {
        ActivityFeedLocal(0, type, title, description, votesString, issuedAt.time, iconDrawable, activityFeed.voteIconDrawable)
    }
}