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

package jp.co.soramitsu.sora.substrate.response

import jp.co.soramitsu.common.util.ParseModel

class StateQueryResponse(
    val block: String,
    private val changes: List<List<String?>>
) : ParseModel() {
    fun changesAsMap(): Map<String, String?> {
        return changes.associate { it[0]!! to it[1] }
    }

    /**
     * The value of each of [keys], in their order. The node lists every key it was asked for, with
     * null for one that has no storage (an account that never held the token). A key it left out,
     * listed twice, or one that was not asked for means the answer is not the read that was asked
     * for: nothing in it counts, and no balance is taken as zero from it. The same holds for an
     * answer from another block than [atBlock], when the read named one.
     */
    fun valuesOf(keys: List<String>, atBlock: String? = null): List<String?> {
        // A read that names its block is only answered by that block's state.
        check(atBlock == null || requireBlock().equals(atBlock, ignoreCase = true)) { STORAGE_ANSWER_INCOMPLETE }
        val asked = keys.toSet()
        val answered = HashMap<String, String?>(changes.size)
        changes.forEach { change ->
            val key = change.getOrNull(0)
            check(key != null && key in asked && change.size == 2 && !answered.containsKey(key)) {
                STORAGE_ANSWER_INCOMPLETE
            }
            answered[key] = change[1]
        }
        check(answered.size == asked.size) { STORAGE_ANSWER_INCOMPLETE }
        return keys.map { answered[it] }
    }

    /**
     * The hash of the block this answer is the state of. The parser leaves the field empty when
     * the node's answer has none; an answer without a block hash cannot say which state it is.
     */
    fun requireBlock(): String {
        val hash: String? = block
        check(hash != null && BLOCK_HASH.matches(hash)) { STORAGE_ANSWER_INCOMPLETE }
        return hash
    }

    companion object {
        const val STORAGE_ANSWER_INCOMPLETE = "SORA2_STORAGE_ANSWER_INCOMPLETE"
        private val BLOCK_HASH = Regex("^0x[0-9a-fA-F]{64}$")
    }
}
