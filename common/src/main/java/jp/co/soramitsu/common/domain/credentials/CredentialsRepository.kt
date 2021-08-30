/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain.credentials

import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import java.security.KeyPair

interface CredentialsRepository {

    suspend fun isMnemonicValid(mnemonic: String): Boolean

    suspend fun generateUserCredentials()

    suspend fun restoreUserCredentials(mnemonic: String)

    fun saveMnemonic(mnemonic: String)

    suspend fun retrieveMnemonic(): String

    suspend fun retrieveIrohaKeyPair(): KeyPair

    suspend fun retrieveKeyPair(): Keypair

    suspend fun getIrohaAddress(): String

    suspend fun getClaimSignature(): String

    fun getAddress(): String

    fun getAccountId(): ByteArray

    suspend fun isAddressOk(address: String): Boolean
}
