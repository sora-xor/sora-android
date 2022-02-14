/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain.credentials

import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import java.security.KeyPair

interface CredentialsDatasource {

    suspend fun saveAddress(address: String)

    suspend fun getAddress(): String

    suspend fun saveKeys(keyPair: Sr25519Keypair)

    suspend fun retrieveKeys(): Sr25519Keypair?

    suspend fun saveMnemonic(mnemonic: String)

    suspend fun retrieveMnemonic(): String

    suspend fun saveIrohaKeys(keyPair: KeyPair)

    suspend fun retrieveIrohaKeys(): KeyPair?

    suspend fun saveIrohaAddress(address: String)

    suspend fun getIrohaAddress(): String

    suspend fun saveSignature(signature: ByteArray)

    suspend fun retrieveSignature(): ByteArray
}
