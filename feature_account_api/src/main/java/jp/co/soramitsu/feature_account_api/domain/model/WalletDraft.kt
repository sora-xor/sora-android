/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
    }

    @Volatile
    private var destroyed = false

    private var mnemonicValue: String? = mnemonic
    private var seedValue: String = seedHex
    private val keypairValue: Sr25519Keypair = keypair

    /** Space-separated phrase words, or null for a raw-seed import. */
    val mnemonic: String?
        get() {
            check(!destroyed) { "WALLET_DRAFT_DESTROYED" }
            return mnemonicValue
        }

    /** 32-byte SORA2 seed as lowercase hex without a prefix. */
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
