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

package jp.co.soramitsu.common.util.json_decoder

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.json.JsonSeedEncoder
import org.bouncycastle.util.encoders.Base64

class JsonAccountsEncoder(
    private val gson: Gson,
    private val cryptoAssistant: CryptoAssistant,
    private val jsonSeedEncoder: JsonSeedEncoder
) {

    fun generate(account: ExportAccount, password: String, genesisHash: String): String {
        return jsonSeedEncoder.generate(
            account.keypair,
            account.seed,
            password,
            account.name,
            MultiChainEncryption.Substrate(EncryptionType.SR25519),
            genesisHash,
            account.address
        )
    }

    fun generate(
        accounts: List<ExportAccount>,
        password: String,
        genesisHash: String,
    ): String {
        val accountsData =
            accounts.map {
                JsonAccountData.Account(it.address, JsonAccountData.Meta(name = it.name, genesisHash = genesisHash))
            }
        val encoded = formEncodedField(accounts, password, genesisHash)

        val importData = JsonAccountData(
            encoded = encoded,
            accounts = accountsData,
        )

        return gson.toJson(importData)
    }

    private fun formEncodedField(
        accounts: List<ExportAccount>,
        password: String,
        genesisHash: String,
    ): String {
        val jsonArray = JsonArray()

        accounts.forEach {
            jsonArray.add(
                gson.fromJson(
                    jsonSeedEncoder.generate(
                        it.keypair,
                        it.seed,
                        password,
                        it.name,
                        MultiChainEncryption.Substrate(EncryptionType.SR25519),
                        genesisHash,
                        it.address
                    ),
                    JsonElement::class.java
                )
            )
        }

        val key = cryptoAssistant.generateKeyForJson(password.toByteArray())
        return Base64.toBase64String(
            key.salt + key.N + key.p + key.r + cryptoAssistant.encryptNaCl(
                gson.toJson(jsonArray),
                key.result.copyOfRange(0, 32)
            )
        )
    }

    data class ExportAccount(
        val keypair: Keypair,
        val seed: ByteArray,
        val name: String,
        val address: String
    )
}
