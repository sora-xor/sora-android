/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data

import jp.co.soramitsu.common.util.EncryptionUtil

class EncryptedPreferences(
    private val soraPreferences: SoraPreferences,
    private val encryptionUtil: EncryptionUtil
) {

    suspend fun putEncryptedString(field: String, value: String) {
        soraPreferences.putString(field, encryptionUtil.encrypt(value))
    }

    suspend fun getDecryptedString(field: String): String {
        val encryptedString = soraPreferences.getString(field)

        return if (encryptedString.isEmpty()) "" else encryptionUtil.decrypt(encryptedString)
    }

    suspend fun clear(field: String) {
        soraPreferences.clear(field)
    }

    suspend fun clear(fields: List<String>) {
        soraPreferences.clear(fields)
    }
}
