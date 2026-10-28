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

package jp.co.soramitsu.common.data

import jp.co.soramitsu.common.util.EncryptionUtil

class EncryptedPreferences(
    private val soraPreferences: SoraPreferences,
    private val encryptionUtil: EncryptionUtil
) {

    suspend fun putEncryptedString(field: String, value: String) {
        soraPreferences.putString(field, encryptionUtil.encrypt(value))
    }

    /** Encrypts [value] for storage without writing it. */
    fun encrypt(value: String): String = encryptionUtil.encrypt(value)

    /** Proves the stored key material can be unlocked on this device; writes nothing. */
    fun requireKeyMaterialReadable() = encryptionUtil.requireKeyMaterialReadable()

    suspend fun getDecryptedString(field: String): String {
        val encryptedString = soraPreferences.getString(field)

        return if (encryptedString.isEmpty()) "" else encryptionUtil.decrypt(encryptedString)
    }

}
