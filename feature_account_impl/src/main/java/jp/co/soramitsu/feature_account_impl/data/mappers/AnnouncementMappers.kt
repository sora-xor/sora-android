/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.core_db.model.AnnouncementLocal
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeedAnnouncement
import jp.co.soramitsu.feature_account_impl.data.network.model.AnnouncementRemote

fun mapAnnoucementRemoteToAnnouncement(announcementRemote: AnnouncementRemote): ActivityFeedAnnouncement {
    return with(announcementRemote) {
        ActivityFeedAnnouncement(message, publicationDate)
    }
}

fun mapAnnouncementToAnnouncementLocal(announcement: ActivityFeedAnnouncement): AnnouncementLocal {
    return with(announcement) {
        AnnouncementLocal(0, message, publicationDate)
    }
}

fun mapAnnouncementLocalToAnnouncement(announcement: AnnouncementLocal): ActivityFeedAnnouncement {
    return with(announcement) {
        ActivityFeedAnnouncement(message, publicationDate)
    }
}