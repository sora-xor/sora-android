package jp.co.soramitsu.feature_account_api.domain.model

import java.util.Date

data class ActivityFeed(
    val type: String,
    val title: String,
    val description: String,
    val votesString: String,
    val issuedAt: Date,
    val iconDrawable: Int,
    val voteIconDrawable: Int = -1
)