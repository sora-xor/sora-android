/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.data.mappers

import jp.co.soramitsu.core_db.model.DiscussionLinkLocal
import jp.co.soramitsu.feature_votable_api.domain.model.project.DiscussionLink
import jp.co.soramitsu.feature_votable_impl.data.network.model.DiscussionLinkRemote

fun mapDiscussionLinkRemoteToDiscussionLink(discussionLinkRemote: DiscussionLinkRemote): DiscussionLink {
    return with(discussionLinkRemote) {
        DiscussionLink(
            title,
            link
        )
    }
}

fun mapDiscussionLinkToDiscussionLinkLocal(discussionLink: DiscussionLink): DiscussionLinkLocal {
    return with(discussionLink) {
        DiscussionLinkLocal(
            title,
            link
        )
    }
}

fun mapDiscussionLinkLocalToDiscussionLink(discussionLinkLocal: DiscussionLinkLocal): DiscussionLink {
    return with(discussionLinkLocal) {
        DiscussionLink(
            title,
            link
        )
    }
}