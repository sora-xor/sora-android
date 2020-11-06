/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.common.util.ActivityFeedTypes
import jp.co.soramitsu.core_db.model.ActivityFeedLocal
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import jp.co.soramitsu.feature_account_impl.R
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class ActivityFeedMappersTest {

    private val activityFeedLocal = ActivityFeedLocal(
        0,
        ActivityFeedTypes.FRIEND_REGISTERED.toString(),
        "title",
        "description",
        "10.0",
        100,
        R.drawable.icon_activity_invite,
        -1
    )
    private val activityFeed = ActivityFeed(
        ActivityFeedTypes.FRIEND_REGISTERED.toString(),
        "title",
        "description",
        "10.0",
        Date(100),
        R.drawable.icon_activity_invite
    )

    @Test
    fun `map activity feed local to activity feed called`() {
        assertEquals(activityFeed, mapActivityFeedLocalToActivityFeed(activityFeedLocal))
    }

    @Test
    fun `map activity feed to activity feed local called`() {
        assertEquals(activityFeedLocal, mapActivityFeedToActivityFeedLocal(activityFeed))
    }
}