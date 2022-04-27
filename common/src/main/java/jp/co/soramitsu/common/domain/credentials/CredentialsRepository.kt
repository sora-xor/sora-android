/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain.credentials

import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import java.security.KeyPair

interface CredentialsRepository {

    suspend fun isMnemonicValid(mnemonic: String): Boolean

    suspend fun generateUserCredentials(accountName: String): SoraAccount

    suspend fun restoreUserCredentials(mnemonic: String, accountName: String): SoraAccount

    suspend fun saveMnemonic(mnemonic: String, soraAccount: SoraAccount)

    suspend fun retrieveMnemonic(soraAccount: SoraAccount): String

    suspend fun retrieveKeyPair(soraAccount: SoraAccount): Sr25519Keypair

    suspend fun saveKeyPair(key: Sr25519Keypair, soraAccount: SoraAccount)

    suspend fun getIrohaData(soraAccount: SoraAccount): IrohaData

    suspend fun getAddressForMigration(): String

    suspend fun isAddressOk(address: String): Boolean
}
