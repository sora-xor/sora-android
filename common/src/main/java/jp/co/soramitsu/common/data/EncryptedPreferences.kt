/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data

import jp.co.soramitsu.common.util.EncryptionUtil

class EncryptedPreferences(
    private val preferences: Preferences,
    private val encryptionUtil: EncryptionUtil
) {

    fun putEncryptedString(field: String, value: String) {
        preferences.putString(field, encryptionUtil.encrypt(value))
    }

    fun getDecryptedString(field: String): String {
        val encryptedString = preferences.getString(field)

        return if (encryptedString.isEmpty()) "" else encryptionUtil.decrypt(encryptedString)
    }
}
