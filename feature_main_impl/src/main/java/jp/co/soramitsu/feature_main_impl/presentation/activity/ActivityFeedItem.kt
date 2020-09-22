/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.activity

data class ActivityFeedItem(
    val type: String,
    val title: String,
    val description: String,
    val votesString: String,
    val issuedAtString: String,
    val iconDrawable: Int,
    val listItemType: Type,
    val votesRightDrawable: Int
) {
    enum class Type {
        LAST_OF_THE_DAY,
        DURING_THE_DAY,
        FIRST_OF_THE_DAY,
        THE_ONLY_EVENT_OF_THE_DAY
    }
}