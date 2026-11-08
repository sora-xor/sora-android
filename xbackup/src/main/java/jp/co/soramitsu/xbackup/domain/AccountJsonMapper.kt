/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.domain

import java.io.File
import jp.co.soramitsu.xbackup.domain.exceptions.DecodingException
import jp.co.soramitsu.xbackup.domain.models.EncryptedBackupAccount
import jp.co.soramitsu.xbackup.utils.JsonFactory
import kotlinx.serialization.json.Json

internal class AccountJsonMapper(private val json: Json = JsonFactory.create()) {
    fun mapEncryptedAccountToJsonFile(
        filePath: String,
        encryptedAccount: EncryptedBackupAccount,
    ): File {
        val jsonString = json.encodeToString(EncryptedBackupAccount.serializer(), encryptedAccount)
        val file = File(filePath + encryptedAccount.address + ".json")
        file.writeText(jsonString)

        return file
    }

    fun mapJsonStringToEncryptedAccount(jsonString: String): EncryptedBackupAccount {
        return try {
            json.decodeFromString(EncryptedBackupAccount.serializer(), jsonString)
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization's SerializationException is an IllegalArgumentException.
            throw DecodingException(e)
        }
    }
}
