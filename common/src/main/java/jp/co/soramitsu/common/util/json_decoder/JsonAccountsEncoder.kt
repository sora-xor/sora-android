/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.json_decoder

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.shared_utils.encrypt.EncryptionType
import jp.co.soramitsu.shared_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.shared_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import org.spongycastle.util.encoders.Base64

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
