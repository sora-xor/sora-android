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
import com.google.gson.JsonObject
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.xsubstrate.encrypt.json.JsonSeedDecodingException
import jp.co.soramitsu.xsubstrate.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.TypeCoderFactory
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.decode
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
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

    /** One sr25519 account read back from an encrypted JSON export. The caller wipes [keypair]. */
    class ImportedAccount(
        val keypair: Sr25519Keypair,
        val name: String?,
    )

    /** Why an encrypted JSON export could not be read. Nothing was changed. */
    sealed class JsonImportException(code: String) : Exception(code) {
        /** Not an encrypted sr25519 account export of the supported format. */
        class Invalid : JsonImportException("JSON_BACKUP_INVALID")

        /** The password does not open the file. */
        class WrongPassword : JsonImportException("JSON_BACKUP_WRONG_PASSWORD")
    }

    /**
     * True when [json] has the shape of an export [decode] can read (one account or a batch).
     * Nothing is decrypted.
     */
    fun looksLikeExport(json: String): Boolean = runCatching { exportKind(json) }.isSuccess

    /**
     * Reads back what [generate] wrote: the export of one account, or the batch export of
     * several accounts (each account inside a batch is itself a one-account export under the
     * same password). Only sr25519 accounts encrypted with scrypt and xsalsa20-poly1305 are
     * accepted, with exactly the scrypt cost this app and polkadot.js write, so a crafted file
     * cannot ask for unbounded memory.
     *
     * @throws JsonImportException
     */
    fun decode(json: String, password: String): List<ImportedAccount> {
        val (root, batch) = exportKind(json)
        if (!batch) return listOf(decodeAccount(root, password))

        val outer = encodedBytes(root)
        val plain = try {
            TypeCoderFactory.getDecoder(ENCRYPTION)?.decode(outer, password.encodeToByteArray())
        } catch (_: Exception) {
            throw JsonImportException.Invalid()
        } ?: throw JsonImportException.WrongPassword()
        val accounts = try {
            gson.fromJson(plain.decodeToString(), JsonArray::class.java)
        } catch (_: Exception) {
            null
        } finally {
            plain.fill(0)
        }
        if (accounts == null || accounts.size() == 0 || accounts.size() > MAX_BATCH_ACCOUNTS) {
            throw JsonImportException.Invalid()
        }
        val decoded = mutableListOf<ImportedAccount>()
        try {
            accounts.forEach { element ->
                val account = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
                    ?: throw JsonImportException.Invalid()
                if (content(account) != ACCOUNT_CONTENT || type(account) != ENCRYPTION) {
                    throw JsonImportException.Invalid()
                }
                decoded += decodeAccount(account, password)
            }
        } catch (error: Throwable) {
            decoded.forEach { it.keypair.privateKey.fill(0); it.keypair.nonce.fill(0) }
            throw error
        }
        return decoded
    }

    /** The parsed export and whether it is a batch. Throws when it is neither kind. */
    private fun exportKind(json: String): Pair<JsonObject, Boolean> {
        if (json.length > MAX_JSON_CHARS) throw JsonImportException.Invalid()
        val root = try {
            gson.fromJson(json, JsonElement::class.java)
        } catch (_: Exception) {
            null
        }?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: throw JsonImportException.Invalid()
        if (type(root) != ENCRYPTION) throw JsonImportException.Invalid()
        return when (content(root)) {
            ACCOUNT_CONTENT -> root to false
            BATCH_CONTENT -> root to true
            else -> throw JsonImportException.Invalid()
        }
    }

    private fun decodeAccount(account: JsonObject, password: String): ImportedAccount {
        // Checks the scrypt header before the library derives a key from it.
        encodedBytes(account).fill(0)
        val decoded = try {
            JsonSeedDecoder(gson).decode(gson.toJson(account), password)
        } catch (_: JsonSeedDecodingException.IncorrectPasswordException) {
            throw JsonImportException.WrongPassword()
        } catch (_: Exception) {
            throw JsonImportException.Invalid()
        }
        val keypair = decoded.keypair as? Sr25519Keypair ?: throw JsonImportException.Invalid()
        if (
            (decoded.multiChainEncryption as? MultiChainEncryption.Substrate)?.encryptionType !=
            EncryptionType.SR25519 ||
            keypair.privateKey.size != KEY_BYTES ||
            keypair.nonce.size != KEY_BYTES ||
            keypair.publicKey.size != KEY_BYTES
        ) {
            keypair.privateKey.fill(0)
            keypair.nonce.fill(0)
            throw JsonImportException.Invalid()
        }
        return ImportedAccount(keypair, decoded.username?.takeIf(String::isNotBlank))
    }

    /** The decoded `encoded` field, after its scrypt parameters were checked. */
    private fun encodedBytes(export: JsonObject): ByteArray {
        val bytes = try {
            Base64.decode(export.get("encoded").asString)
        } catch (_: Exception) {
            throw JsonImportException.Invalid()
        }
        if (bytes.size <= SCRYPT_HEADER_BYTES + SECRET_BOX_OVERHEAD_BYTES) {
            throw JsonImportException.Invalid()
        }
        fun littleEndianInt(offset: Int): Long =
            (0 until 4).fold(0L) { value, index ->
                value or ((bytes[offset + index].toLong() and 0xff) shl (8 * index))
            }
        if (
            littleEndianInt(SCRYPT_SALT_BYTES) != SCRYPT_N ||
            littleEndianInt(SCRYPT_SALT_BYTES + 4) != SCRYPT_P ||
            littleEndianInt(SCRYPT_SALT_BYTES + 8) != SCRYPT_R
        ) {
            throw JsonImportException.Invalid()
        }
        return bytes
    }

    private fun content(export: JsonObject): List<String> = strings(export, "content")

    private fun type(export: JsonObject): List<String> = strings(export, "type")

    private fun strings(export: JsonObject, field: String): List<String> = try {
        export.getAsJsonObject("encoding").getAsJsonArray(field).map { it.asString }
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        val ACCOUNT_CONTENT = listOf("pkcs8", "sr25519")
        val BATCH_CONTENT = listOf("batch-pkcs8")
        val ENCRYPTION = listOf("scrypt", "xsalsa20-poly1305")
        const val KEY_BYTES = 32
        const val MAX_JSON_CHARS = 512 * 1024
        const val MAX_BATCH_ACCOUNTS = 100
        const val SCRYPT_SALT_BYTES = 32
        const val SCRYPT_HEADER_BYTES = SCRYPT_SALT_BYTES + 12
        const val SECRET_BOX_OVERHEAD_BYTES = 24 + 16
        const val SCRYPT_N = 32768L
        const val SCRYPT_P = 1L
        const val SCRYPT_R = 8L
    }
}
