/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class CryptoType {
    SR25519,
    ED25519,
    ECDSA,
}

data class BackupAccountMeta(
    val name: String,
    val address: String,
)

data class DecryptedBackupAccount(
    val name: String,
    val address: String,
    val mnemonicPhrase: String? = null,
    val cryptoType: CryptoType,
    val substrateDerivationPath: String? = null,
    val ethDerivationPath: String? = null,
    val backupAccountType: List<BackupAccountType>,
    val seed: Seed? = null,
    val json: Json? = null,
)

/** The JSON stored in Drive. Field names are the backup file format: never rename them. */
@Serializable
data class EncryptedBackupAccount(
    @SerialName("name") val name: String,
    @SerialName("address") val address: String,
    @SerialName("encryptedMnemonicPhrase") val encryptedMnemonicPhrase: String? = null,
    @SerialName("encryptedEthDerivationPath") val encryptedEthDerivationPath: String? = null,
    @SerialName("encryptedSubstrateDerivationPath") val encryptedSubstrateDerivationPath: String? = null,
    @SerialName("cryptoType") val cryptoType: String,
    @SerialName("backupAccountType") val backupAccountType: List<String>,
    @SerialName("encryptedSeed") val encryptedSeed: Seed? = null,
    @SerialName("json") val json: Json? = null,
)

enum class BackupAccountType {
    PASSPHRASE,
    JSON,
    SEED,
}

@Serializable
data class Json(
    @SerialName("substrateJson") val substrateJson: String? = null,
    @SerialName("ethJson") val ethJson: String? = null,
)

@Serializable
data class Seed(
    @SerialName("substrateSeed") val substrateSeed: String? = null,
    @SerialName("ethSeed") val ethSeed: String? = null,
)
