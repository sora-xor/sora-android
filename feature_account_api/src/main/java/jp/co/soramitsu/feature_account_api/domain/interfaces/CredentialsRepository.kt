/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair

interface CredentialsRepository {

    suspend fun isMnemonicValid(mnemonic: String): Boolean

    suspend fun isRawSeedValid(rawSeed: String): Boolean

    suspend fun generateUserCredentials(accountName: String): SoraAccount

    suspend fun restoreUserCredentialsFromMnemonic(mnemonic: String, accountName: String): SoraAccount

    suspend fun restoreUserCredentialsFromRawSeed(rawSeed: String, accountName: String): SoraAccount

    suspend fun saveMnemonic(mnemonic: String, soraAccount: SoraAccount)

    suspend fun retrieveMnemonic(soraAccount: SoraAccount): String

    suspend fun retrieveSeed(soraAccount: SoraAccount): String

    suspend fun retrieveKeyPair(soraAccount: SoraAccount): Sr25519Keypair

    suspend fun saveKeyPair(key: Sr25519Keypair, soraAccount: SoraAccount)

    suspend fun getIrohaData(soraAccount: SoraAccount): IrohaData

    suspend fun getAddressForMigration(): String

    suspend fun generateJson(accounts: List<SoraAccount>, password: String): String
}
