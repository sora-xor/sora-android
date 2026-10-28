/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/


package jp.co.soramitsu.feature_blockexplorer_api.presentation.screen

import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel

/**
 * Returns one LazyColumn key per item, guaranteed unique within the list.
 *
 * Compose crashes when two items in one LazyColumn share a key. The history handler already drops
 * repeated transactions, but the list must not depend on that: a pending transaction listed first
 * can produce a second date header with the same title, and future data sources may repeat rows.
 * Keys stay stable for the common case (the transaction hash or the header title); only a repeat
 * gets a numbered suffix.
 */
internal fun historyListKeys(items: List<EventUiModel>): List<String> {
    val used = HashSet<String>(items.size * 2)
    return items.map { event ->
        val base = when (event) {
            is EventUiModel.EventTimeSeparatorUiModel -> "header:${event.title}"
            is EventUiModel.EventTxUiModel -> "tx:${event.txHash}"
            EventUiModel.EventUiLoading -> "loading"
        }
        var key = base
        var repeat = 0
        while (!used.add(key)) {
            repeat++
            key = "$base#$repeat"
        }
        key
    }
}
