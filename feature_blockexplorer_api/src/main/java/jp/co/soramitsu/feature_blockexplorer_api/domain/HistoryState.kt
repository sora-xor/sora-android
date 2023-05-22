/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.domain

import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel

sealed interface HistoryState {
    object Loading : HistoryState
    object Error : HistoryState
    object NoData : HistoryState
    data class History(
        val endReached: Boolean,
        val events: List<EventUiModel>,
        val hasErrorLoadingNew: Boolean = false,
    ) : HistoryState
}
