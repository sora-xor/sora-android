/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup

import jp.co.soramitsu.xbackup.domain.AccountJsonMapper
import jp.co.soramitsu.xbackup.domain.BackupAccountCrypto
import jp.co.soramitsu.xbackup.domain.exceptions.DecodingException
import jp.co.soramitsu.xbackup.domain.exceptions.DecryptionException
import jp.co.soramitsu.xbackup.domain.models.BackupAccountType
import jp.co.soramitsu.xbackup.domain.models.CryptoType
import jp.co.soramitsu.xbackup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.xbackup.domain.models.EncryptedBackupAccount
import jp.co.soramitsu.xbackup.domain.models.Json
import jp.co.soramitsu.xbackup.domain.models.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The less common shapes of a backup: Ethereum parts, JSON-only accounts and broken files. */
class BackupAccountCryptoBranchesTest {
    private val crypto = BackupAccountCrypto()

    private val ethereumAccount = DecryptedBackupAccount(
        name = "Both",
        address = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
        cryptoType = CryptoType.ECDSA,
        substrateDerivationPath = "//0",
        ethDerivationPath = "m/44'/60'/0'/0/0",
        backupAccountType = listOf(BackupAccountType.SEED, BackupAccountType.JSON),
        seed = Seed(substrateSeed = "0x01", ethSeed = "0x02"),
        json = Json(substrateJson = "{\"s\":1}", ethJson = "{\"e\":1}"),
    )

    private fun encryptedSeedOnly(seed: Seed?) = EncryptedBackupAccount(
        name = "n",
        address = "a",
        cryptoType = "SR25519",
        backupAccountType = listOf("seed"),
        encryptedSeed = seed,
    )

    @Test
    fun `seeds, derivation paths and json all round trip`() {
        val encrypted = crypto.encryptAccount(ethereumAccount, "pw")
        assertEquals(listOf("seed", "json"), encrypted.backupAccountType)

        assertEquals(ethereumAccount, crypto.decryptAccount(encrypted, "pw"))
    }

    @Test
    fun `an ethereum-only seed round trips`() {
        val account = ethereumAccount.copy(seed = Seed(ethSeed = "0x02"), backupAccountType = listOf(BackupAccountType.SEED), json = null)
        assertEquals(account, crypto.decryptAccount(crypto.encryptAccount(account, "pw"), "pw"))
    }

    @Test
    fun `a seed backup without its seed is rejected`() {
        val missing = assertThrows(IllegalArgumentException::class.java) {
            crypto.decryptAccount(encryptedSeedOnly(null), "pw")
        }
        assertEquals("SEED is selected but encryptedSeed is null", missing.message)

        val empty = assertThrows(IllegalArgumentException::class.java) {
            crypto.decryptAccount(encryptedSeedOnly(Seed()), "pw")
        }
        assertEquals(
            "SEED is selected but encryptedSeed.ethSeed == null and encryptedSeed.substrateSeed == null",
            empty.message,
        )
    }

    @Test
    fun `seeds that do not decrypt with the password are rejected`() {
        val encrypted = crypto.encryptAccount(ethereumAccount.copy(backupAccountType = listOf(BackupAccountType.SEED)), "right")

        val error = assertThrows(IllegalArgumentException::class.java) { crypto.decryptAccount(encrypted, "wrong") }
        assertEquals("SEED is selected but ethSeed == null and substrateSeed == null", error.message)
    }

    @Test
    fun `selected parts that are missing are rejected`() {
        val passphrase = encryptedSeedOnly(null).copy(backupAccountType = listOf("passphrase"))
        assertThrows(IllegalArgumentException::class.java) { crypto.decryptAccount(passphrase, "pw") }

        val json = encryptedSeedOnly(null).copy(backupAccountType = listOf("json"))
        assertThrows(IllegalArgumentException::class.java) { crypto.decryptAccount(json, "pw") }
    }

    @Test
    fun `ciphertext that is not hex cannot be decrypted`() {
        val broken = encryptedSeedOnly(null).copy(backupAccountType = listOf("passphrase"), encryptedMnemonicPhrase = "not hex")
        assertThrows(DecryptionException::class.java) { crypto.decryptAccount(broken, "pw") }
    }

    @Test
    fun `json that is not a backup is a decoding error`() {
        assertThrows(DecodingException::class.java) {
            AccountJsonMapper().mapJsonStringToEncryptedAccount("{\"name\":\"only a name\"}")
        }
    }
}
