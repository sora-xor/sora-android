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

package jp.co.soramitsu.feature_account_api.domain.model

import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair

/**
 * A SORA2 wallet held only in memory while the person creates or imports it.
 *
 * Nothing is written to storage until the draft is committed, so backing out of the flow leaves
 * no key material behind. Committing writes only absent values and re-uses values that are
 * already stored for the same address after proving they are identical; it never overwrites.
 * Call [destroy] once the draft is no longer needed.
 */
class WalletDraft(
    val address: String,
    val accountName: String,
    mnemonic: String?,
    seedHex: String,
    keypair: Sr25519Keypair,
    val source: Source,
    /** Zeroes the key pair's secret bytes; supplied by the module that created the key pair. */
    private val wipeKeypair: (Sr25519Keypair) -> Unit,
) {
    enum class Source {
        /** A new random 24-word phrase. */
        GENERATED,

        /** An imported recovery phrase (12, 15, 18, 21 or 24 words). */
        MNEMONIC,

        /** An imported raw seed. */
        RAW_SEED,

        /**
         * A key pair read from an encrypted JSON export. It carries no phrase and no seed
         * ([mnemonic] is null and [seedHex] is empty): the key pair itself is the wallet.
         */
        JSON,
    }

    @Volatile
    private var destroyed = false

    private var mnemonicValue: String? = mnemonic
    private var seedValue: String = seedHex
    private val keypairValue: Sr25519Keypair = keypair

    /** Space-separated phrase words, or null for a raw-seed or JSON import. */
    val mnemonic: String?
        get() {
            check(!destroyed) { "WALLET_DRAFT_DESTROYED" }
            return mnemonicValue
        }

    /** 32-byte SORA2 seed as lowercase hex without a prefix; empty for a JSON import. */
    val seedHex: String
        get() {
            check(!destroyed) { "WALLET_DRAFT_DESTROYED" }
            return seedValue
        }

    val keypair: Sr25519Keypair
        get() {
            check(!destroyed) { "WALLET_DRAFT_DESTROYED" }
            return keypairValue
        }

    val mnemonicWords: List<String>
        get() = mnemonic.orEmpty().trim().split(Regex("\\s+")).filter(String::isNotBlank)

    fun destroy() {
        if (destroyed) return
        destroyed = true
        wipeKeypair(keypairValue)
        mnemonicValue = null
        seedValue = ""
    }
}
