/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import jp.co.soramitsu.xbackup.domain.AccountDatasource
import jp.co.soramitsu.xbackup.domain.AccountJsonMapper
import jp.co.soramitsu.xbackup.domain.BackupAccountCrypto
import jp.co.soramitsu.xbackup.domain.Storage
import jp.co.soramitsu.xbackup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.xbackup.domain.exceptions.StorageQuotaExceeded
import jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta
import jp.co.soramitsu.xbackup.domain.models.BackupAccountType
import jp.co.soramitsu.xbackup.domain.models.CryptoType
import jp.co.soramitsu.xbackup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.xbackup.domain.models.FileMeta
import jp.co.soramitsu.xbackup.domain.models.Json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupServiceTest {

    private class StoredFile(val name: String, val description: String, val content: String)

    private class FakeStorage : Storage {
        val files = linkedMapOf<String, StoredFile>()
        var webFiles = listOf<FileMeta>()
        var signedIn = true
        var saveError: Throwable? = null
        var deleteError: Throwable? = null
        var deletedAll = false
        var loggedOut = false
        val uploaded = mutableListOf<File>()
        private var nextId = 1

        override suspend fun signIn(context: Context, activityResultLauncher: ActivityResultLauncher<Intent>) = signedIn

        override suspend fun saveFile(context: Context, name: String, description: String, file: File): String {
            uploaded += file
            saveError?.let { throw it }
            val id = "file${nextId++}"
            files[id] = StoredFile(name, description, file.readText())
            return id
        }

        override suspend fun getFileContent(context: Context, fileId: String) = files.getValue(fileId).content

        override suspend fun getListOfFileHeads(context: Context) =
            files.map { (id, file) -> FileMeta(fileName = file.name, description = file.description, fileId = id) }

        override suspend fun getWebListOfFileHeads(context: Context) = webFiles

        override suspend fun deleteFile(context: Context, fileId: String) {
            deleteError?.let { throw it }
            files.remove(fileId)
        }

        override suspend fun deleteAllFiles(context: Context) {
            deletedAll = true
            files.clear()
        }

        override suspend fun logout(context: Context) {
            loggedOut = true
        }
    }

    private class FakeDatasource : AccountDatasource {
        val ids = mutableMapOf<String, String>()

        override suspend fun saveFileId(address: String, fileId: String) {
            ids[address] = fileId
        }

        override suspend fun getFileId(address: String) = ids[address].orEmpty()

        override suspend fun removeFileId(address: String) {
            ids.remove(address)
        }

        override suspend fun removeAllFields() = ids.clear()
    }

    private val storage = FakeStorage()
    private val datasource = FakeDatasource()
    private val service = BackupService(TestContext, storage, AccountJsonMapper(), BackupAccountCrypto(), datasource)

    private val account = DecryptedBackupAccount(
        name = "Main",
        address = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
        mnemonicPhrase = "bottom drive obey lake curtain smoke basket hold race lonely fit walk",
        cryptoType = CryptoType.SR25519,
        substrateDerivationPath = "//0",
        backupAccountType = listOf(BackupAccountType.PASSPHRASE),
    )

    @Test
    fun `authorize is the storage sign-in`() = runTest {
        val launcher = RecordingLauncher()
        assertTrue(service.authorize(launcher))
        storage.signedIn = false
        assertFalse(service.authorize(launcher))
    }

    @Test
    fun `saving uploads the encrypted account as address json and remembers its file id`() = runTest {
        service.saveBackupAccount(account, "pw")

        val stored = storage.files.getValue("file1")
        assertEquals("${account.address}.json", stored.name)
        assertEquals("Main", stored.description)
        assertFalse(stored.content.contains("bottom"))
        assertEquals(mapOf(account.address to "file1"), datasource.ids)
        assertTrue(service.isAccountBackedUp(account.address))
        assertFalse(storage.uploaded.single().exists())
    }

    @Test
    fun `a failed upload deletes the temporary file and records nothing`() = runTest {
        storage.saveError = StorageQuotaExceeded(Exception("full"))

        val error = runCatching { service.saveBackupAccount(account, "pw") }.exceptionOrNull()

        assertTrue(error is StorageQuotaExceeded)
        assertFalse(storage.uploaded.single().exists())
        assertFalse(service.isAccountBackedUp(account.address))
    }

    @Test
    fun `listing backups replaces the known file ids and an import decrypts with the password`() = runTest {
        service.saveBackupAccount(account, "pw")
        datasource.ids["stale"] = "old"

        val backups = service.getBackupAccounts()

        assertEquals(listOf(BackupAccountMeta(name = "Main", address = account.address)), backups)
        assertEquals(mapOf(account.address to "file1"), datasource.ids)
        assertEquals(account, service.importBackupAccount(account.address, "pw"))
    }

    @Test
    fun `web backups pair each substrate file with its ethereum file`() = runTest {
        storage.webFiles = listOf(
            FileMeta(fileName = "Web wallet.json", description = "addrA/eth1", fileId = "sub1"),
            FileMeta(fileName = "eth.json", description = "", fileId = "eth1"),
            FileMeta(fileName = "Solo.json", description = "addrB", fileId = "sub2"),
        )
        datasource.ids["addrB"] = "known"

        val backups = service.getWebBackupAccounts()

        assertEquals(
            listOf(BackupAccountMeta(name = "Web wallet", address = "addrA"), BackupAccountMeta(name = "Solo", address = "addrB")),
            backups,
        )
        assertEquals("sub1/eth1", datasource.ids["addrA"])
        assertEquals("known", datasource.ids["addrB"])
    }

    @Test
    fun `a web import reads the substrate json and the ethereum json when there is one`() = runTest {
        storage.files["sub1"] = StoredFile("w.json", "addrA/eth1", "{\"substrate\":1}")
        storage.files["eth1"] = StoredFile("e.json", "", "{\"eth\":1}")
        datasource.ids["addrA"] = "sub1/eth1"
        datasource.ids["addrB"] = "sub1"

        val both = service.importWebBackupAccount("addrA", "Web wallet")
        assertEquals(Json(substrateJson = "{\"substrate\":1}", ethJson = "{\"eth\":1}"), both.json)
        assertEquals(listOf(BackupAccountType.JSON), both.backupAccountType)
        assertEquals("Web wallet", both.name)
        assertEquals("addrA", both.address)

        val substrateOnly = service.importWebBackupAccount("addrB", "Solo")
        assertEquals(Json(substrateJson = "{\"substrate\":1}", ethJson = null), substrateOnly.json)
    }

    @Test
    fun `deleting a backup forgets its file id, also when Drive no longer has the file`() = runTest {
        storage.files["f1"] = StoredFile("a.json", "A", "{}")
        datasource.ids["a"] = "f1"
        service.deleteBackupAccount("a")
        assertTrue(storage.files.isEmpty())
        assertFalse(service.isAccountBackedUp("a"))

        datasource.ids["b"] = "gone"
        storage.deleteError = FileNotFoundException(Exception("404"))
        val error = runCatching { service.deleteBackupAccount("b") }.exceptionOrNull()
        assertTrue(error is FileNotFoundException)
        assertFalse(service.isAccountBackedUp("b"))
    }

    @Test
    fun `deleting everything and logging out forget all file ids`() = runTest {
        datasource.ids["a"] = "f1"
        service.deleteAllAccounts()
        assertTrue(storage.deletedAll)
        assertTrue(datasource.ids.isEmpty())

        datasource.ids["b"] = "f2"
        service.logout()
        assertTrue(storage.loggedOut)
        assertTrue(datasource.ids.isEmpty())
    }

    @Test
    fun `create builds the Drive-backed service from a context`() {
        assertNotNull(BackupService.create(TestContext))
    }
}
