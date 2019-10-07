/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import jp.co.soramitsu.common.R

enum class MainTabs(
    val stringResource: Int,
    val index: Int
) {
    ALL(R.string.tabs_all, 0),
    VOTED(R.string.tabs_voted, 1),
    FAVOURITE(R.string.tabs_favourites, 2),
    COMPLETED(R.string.tabs_complete, 3);

    companion object {
        fun fromIndex(index: Int): MainTabs = when (index) {
            ALL.index -> ALL
            VOTED.index -> VOTED
            FAVOURITE.index -> FAVOURITE
            COMPLETED.index -> COMPLETED
            else -> ALL
        }
    }
}