/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.response

import androidx.annotation.Keep

@Keep
class StateQueryResponse(
    val block: String,
    private val changes: List<List<String?>>
) {
    fun changesAsMap(): Map<String, String?> {
        return changes.associate { it[0]!! to it[1] }
    }
}
