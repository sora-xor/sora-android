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
import jp.co.soramitsu.xbackup.domain.models.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupAccountCryptoTest {

    private val account = DecryptedBackupAccount(
        name = "Main",
        address = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
        mnemonicPhrase = "bottom drive obey lake curtain smoke basket hold race lonely fit walk",
        cryptoType = CryptoType.SR25519,
        substrateDerivationPath = "//0",
        backupAccountType = listOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED),
        seed = Seed(substrateSeed = "0xfac7959dbfe72f052e5a0c3c8d6530f202b02fd8f9f5ca3580ec8deb7797479e"),
    )

    @Test
    fun `encrypted backup round trips with the password and keeps the file format`() {
        val crypto = BackupAccountCrypto()
        val encrypted = crypto.encryptAccount(account, "correct horse")

        assertFalse(encrypted.encryptedMnemonicPhrase!!.contains("bottom"))
        assertEquals(listOf("passphrase", "seed"), encrypted.backupAccountType)
        assertEquals("SR25519", encrypted.cryptoType)

        val json = java.io.File.createTempFile("backup", ".json").also { it.delete() }
        val file = AccountJsonMapper().mapEncryptedAccountToJsonFile(json.parent + java.io.File.separator, encrypted)
        val text = file.readText()
        file.delete()
        for (field in listOf("\"name\"", "\"address\"", "\"encryptedMnemonicPhrase\"", "\"cryptoType\"",
                "\"backupAccountType\"", "\"encryptedSeed\"", "\"substrateSeed\"")) {
            assertTrue(field, text.contains(field))
        }

        val decrypted = crypto.decryptAccount(AccountJsonMapper().mapJsonStringToEncryptedAccount(text), "correct horse")
        assertEquals(account.copy(ethDerivationPath = null, json = null), decrypted)
    }

    @Test(expected = DecryptionException::class)
    fun `a wrong password does not decrypt the phrase`() {
        val crypto = BackupAccountCrypto()
        val encrypted = crypto.encryptAccount(account.copy(backupAccountType = listOf(BackupAccountType.PASSPHRASE)), "right")
        crypto.decryptAccount(encrypted, "wrong")
    }

    @Test(expected = DecodingException::class)
    fun `malformed backup json is a decoding error`() {
        AccountJsonMapper().mapJsonStringToEncryptedAccount("{not json")
    }
}
