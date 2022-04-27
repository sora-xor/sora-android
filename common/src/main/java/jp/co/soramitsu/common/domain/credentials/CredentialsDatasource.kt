/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain.credentials

import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair

interface CredentialsDatasource {

    suspend fun getAddress(): String

    suspend fun saveKeys(keyPair: Sr25519Keypair, suffixAddress: String)

    suspend fun retrieveKeys(suffixAddress: String): Sr25519Keypair?

    suspend fun saveMnemonic(mnemonic: String, suffixAddress: String)

    suspend fun retrieveMnemonic(suffixAddress: String): String
}
